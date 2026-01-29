package app;

import static spark.Spark.*;
import java.sql.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.IOException;

public class App {

    // --- 1. CONFIGURACIÓN DE CONEXIÓN (AWS) ---
    private static final String DB_URL = "jdbc:mysql://18.206.19.232:3306/lortu_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
    private static final String DB_USER = "admin_lortu";
    private static final String DB_PASS = obtenerPassword();

    /**
     * Carga la contraseña desde el archivo local db.properties o variable de entorno
     */
    private static String obtenerPassword() {
        Properties propiedades = new Properties();
        try (FileInputStream entradaArchivo = new FileInputStream("db.properties")) {
            propiedades.load(entradaArchivo);
            String contrasena = propiedades.getProperty("db.password");
            if (contrasena != null) return contrasena;
        } catch (IOException e) {
            System.out.println("⚠️ ALERTA: No se pudo leer db.properties localmente.");
        }
        return System.getenv("DB_PASSWORD"); 
    }

    public static void main(String[] args) {
        // Configuración del servidor Spark
        port(4567);
        staticFiles.location("/public"); 

        // --- 2. RUTAS DE NAVEGACIÓN Y CATÁLOGO ---

        get("/", (req, res) -> {
            res.redirect("/html/login.html");
            return null;
        });

        // Ruta para el Catálogo de Cursos (Dinámica con Nombres, Horas y Plazas)
        get("/html/cursoswp.html", (req, res) -> {
            String rutaArchivo = "src/main/resources/public/html/cursos_plantilla.html";
            try {
                byte[] bytesArchivo = Files.readAllBytes(Paths.get(rutaArchivo));
                String contenidoHtml = new String(bytesArchivo, "UTF-8");

                try (Connection conexion = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                    for (int i = 1; i <= 4; i++) {
                        String nombreCurso = obtenerNombreCurso(conexion, i);
                        int horasCurso = obtenerHorasCurso(conexion, i);
                        int maximoPlazas = obtenerPlazasMax(conexion, i);
                        int alumnosInscritos = obtenerContadorInscritos(conexion, i);
                        int plazasDisponibles = maximoPlazas - alumnosInscritos;
                        
                        contenidoHtml = contenidoHtml.replace("NOMBRE_" + i, nombreCurso);
                        contenidoHtml = contenidoHtml.replace("HORAS_" + i, String.valueOf(horasCurso));
                        contenidoHtml = contenidoHtml.replace("TOTAL_" + i, String.valueOf(maximoPlazas));
                        contenidoHtml = contenidoHtml.replace("DISPONIBLES_" + i, String.valueOf(plazasDisponibles));
                    }
                }
                res.type("text/html; charset=UTF-8");
                return contenidoHtml;
            } catch (Exception e) {
                res.status(500);
                return "Error crítico al cargar el catálogo dinámico: " + e.getMessage();
            }
        });

        // Panel de Administración Dinámico
        get("/html/admin.html", (req, res) -> {
            String rutaArchivo = "src/main/resources/admin.html"; 
            try {
                byte[] bytesArchivo = Files.readAllBytes(Paths.get(rutaArchivo));
                String contenidoHtml = new String(bytesArchivo, "UTF-8");
                contenidoHtml = contenidoHtml.replace("{{OPCIONES_CURSOS}}", obtenerHtmlOpcionesCursos());
                res.type("text/html; charset=UTF-8");
                return contenidoHtml;
            } catch (Exception e) {
                res.status(500);
                return "Error al procesar el panel de administración: " + e.getMessage();
            }
        });

        // --- 3. PROCESOS DE USUARIO (LOGIN, REGISTRO, INSCRIPCIÓN) ---

        post("/login", (req, res) -> {
            String correoUsuario = req.queryParams("email");
            String claveUsuario = req.queryParams("password");
            Usuario objetoUsuario = validarUsuario(correoUsuario, claveUsuario);

            if (objetoUsuario == null) {
                res.status(401); 
                return "<html><body><h1>Acceso Denegado</h1><p>Correo o contraseña incorrectos.</p><a href='/html/login.html'>Volver</a></body></html>";
            }

            if (objetoUsuario.getRol().equalsIgnoreCase("admin")) {
                res.redirect("/html/admin.html");
            } else {
                res.redirect("/html/index.html");
            }
            return null;
        });

        post("/registro", (req, res) -> {
            String nombre = req.queryParams("nombre");
            String email = req.queryParams("email");
            String password = req.queryParams("password");
            if (registrarUsuario(nombre, email, password)) {
                res.redirect("/html/login.html");
            } else {
                return "<h1>Error</h1><p>El email ya está en uso.</p><a href='/html/registro.html'>Volver</a>";
            }
            return null;
        });

        post("/inscribir", (req, res) -> {
            String email = req.queryParams("email");
            int idCurso = Integer.parseInt(req.queryParams("id_curso"));
            if (inscribirAlumno(email, idCurso)) {
                return "<body><h1 style='color:green;'>Inscripción Exitosa</h1><a href='/html/index.html'>Volver</a></body>";
            } else {
                return "<h1>Error</h1><p>Hubo un problema al procesar tu inscripción en AWS.</p>";
            }
        });

        // --- 4. RUTAS DE ADMINISTRACIÓN INTERACTIVA ---

        get("/admin/alumnos_curso", (req, res) -> {
            String idDelCurso = req.queryParams("id");
            StringBuilder constructorHtml = new StringBuilder("<!DOCTYPE html><html lang='es'><head><meta charset='UTF-8'><link rel='stylesheet' href='/css/admin.css'></head><body><div class='admin-container' style='padding:50px;'><h1>Alumnos inscritos en el curso seleccionado:</h1><table border='1' style='width:100%; border-collapse:collapse;'>");
            constructorHtml.append("<tr style='background:#ffa500; color:white;'><th>Nombre Alumno</th><th>Email</th></tr>");

            String consultaSql = "SELECT u.nombre, u.email FROM usuarios u JOIN inscripciones i ON u.id_usuario = i.id_usuario WHERE i.id_curso = ?";

            try (Connection conexion = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 PreparedStatement sentencia = conexion.prepareStatement(consultaSql)) {
                sentencia.setInt(1, Integer.parseInt(idDelCurso));
                try (ResultSet resultado = sentencia.executeQuery()) {
                    while (resultado.next()) {
                        constructorHtml.append("<tr><td style='padding:10px;'>").append(resultado.getString("nombre"))
                            .append("</td><td style='padding:10px;'>").append(resultado.getString("email")).append("</td></tr>");
                    }
                }
            } catch (SQLException e) { return "Error SQL: " + e.getMessage(); }

            constructorHtml.append("</table><br><a href='/html/admin.html'>Volver al Panel</a></div></body></html>");
            res.type("text/html; charset=UTF-8");
            return constructorHtml.toString();
        });

        post("/admin/actualizar_curso", (req, res) -> {
            int identificador = Integer.parseInt(req.queryParams("id"));
            int nuevasPlazas = Integer.parseInt(req.queryParams("plazas"));
            int nuevasHoras = Integer.parseInt(req.queryParams("horas"));
            String sentenciaSql = "UPDATE cursos SET plazas_max = ?, horas = ? WHERE id_curso = ?";

            try (Connection conexion = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 PreparedStatement sentencia = conexion.prepareStatement(sentenciaSql)) {
                sentencia.setInt(1, nuevasPlazas);
                sentencia.setInt(2, nuevasHoras);
                sentencia.setInt(3, identificador);
                sentencia.executeUpdate();
                return "<html><body><h2 style='color:green;'>✅ Base de Datos AWS Actualizada</h2><a href='/html/admin.html'>Volver al Panel</a></body></html>";
            } catch (SQLException e) { return "Error al conectar con AWS: " + e.getMessage(); }
        });

        get("/admin/consulta/:id", (req, res) -> {
            int idDeLaConsulta = Integer.parseInt(req.params(":id"));
            res.type("text/html; charset=UTF-8"); 
            return ejecutarConsultaAdmin(idDeLaConsulta);
        });
    }

    // --- 5. MÉTODOS DE APOYO JDBC ---

    private static String obtenerNombreCurso(Connection conexion, int idCurso) {
        String sql = "SELECT nombre FROM cursos WHERE id_curso = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setInt(1, idCurso);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("nombre");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return "Curso No Definido";
    }

    private static int obtenerHorasCurso(Connection conexion, int idCurso) {
        String sql = "SELECT horas FROM cursos WHERE id_curso = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setInt(1, idCurso);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("horas");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    private static int obtenerPlazasMax(Connection conexion, int idCurso) {
        String sql = "SELECT plazas_max FROM cursos WHERE id_curso = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setInt(1, idCurso);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("plazas_max");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    private static int obtenerContadorInscritos(Connection conexion, int idCurso) {
        String sql = "SELECT COUNT(*) FROM inscripciones WHERE id_curso = ?";
        try (PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setInt(1, idCurso);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    private static String obtenerHtmlOpcionesCursos() {
        StringBuilder opciones = new StringBuilder();
        try (Connection conexion = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement estado = conexion.createStatement();
             ResultSet resultado = estado.executeQuery("SELECT id_curso, nombre FROM cursos ORDER BY nombre")) {
            while (resultado.next()) {
                opciones.append("<option value='").append(resultado.getInt("id_curso")).append("'>")
                        .append(resultado.getString("nombre")).append("</option>");
            }
        } catch (SQLException e) { return "<option>Error al cargar de AWS</option>"; }
        return opciones.toString();
    }

    private static Usuario validarUsuario(String email, String pass) {
        String sql = "SELECT id_usuario, nombre, email, password, rol FROM usuarios WHERE email = ? AND password = ?";
        try (Connection conexion = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, pass);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new Usuario(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    private static boolean registrarUsuario(String n, String e, String p) {
        String sql = "INSERT INTO usuarios (nombre, email, password, rol) VALUES (?, ?, ?, 'alumno')";
        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, n); ps.setString(2, e); ps.setString(3, p); return ps.executeUpdate() > 0;
        } catch (Exception ex) { return false; }
    }

    private static boolean inscribirAlumno(String email, int idC) {
        String sql = "INSERT INTO inscripciones (id_usuario, id_curso, fecha_reserva, estado) VALUES ((SELECT id_usuario FROM usuarios WHERE email = ?), ?, CURRENT_DATE, 'Confirmada')";
        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email); ps.setInt(2, idC); return ps.executeUpdate() > 0;
        } catch (Exception ex) { return false; }
    }

    private static boolean existeUsuario(String email) {
        try (Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS); PreparedStatement ps = con.prepareStatement("SELECT id_usuario FROM usuarios WHERE email = ?")) {
            ps.setString(1, email); return ps.executeQuery().next();
        } catch (Exception ex) { return false; }
    }

    // --- 6. MOTOR DE INFORMES SINCRONIZADO CON LOS BOTONES DEL HTML ---

    private static String ejecutarConsultaAdmin(int numeroConsulta) {
        String consultaSql = "";
        String tituloReporte = "";
        
        switch(numeroConsulta) {
            case 1: 
                consultaSql = "SELECT nombre, email FROM usuarios WHERE rol = 'alumno'"; 
                tituloReporte = "1. Listado Oficial de Alumnos"; break;
            case 2: 
                consultaSql = "SELECT nombre, horas, plazas_max FROM cursos"; 
                tituloReporte = "2. Catálogo Vigente de Cursos"; break;
            case 3: 
                consultaSql = "SELECT * FROM aulas"; 
                tituloReporte = "3. Gestión de Infraestructura: Aulas"; break;
            case 4: 
                consultaSql = "SELECT nombre, email FROM usuarios WHERE rol = 'admin'"; 
                tituloReporte = "4. Administradores del Sistema"; break;
            case 5: 
                consultaSql = "SELECT nombre, horas FROM cursos WHERE horas > 80"; 
                tituloReporte = "5. Cursos de Larga Duración (Mas de 80 horas)"; break;
            case 6: 
                consultaSql = "SELECT nombre, horas FROM cursos WHERE subvencionado = 1"; 
                tituloReporte = "6. Cursos con Subvención Activa"; break;
            case 7: 
                consultaSql = "SELECT * FROM inscripciones WHERE estado = 'Confirmada'"; 
                tituloReporte = "7. Inscripciones Confirmadas"; break;
            case 8: 
                consultaSql = "SELECT u.nombre, i.nota_final FROM usuarios u JOIN inscripciones i ON u.id_usuario = i.id_usuario WHERE i.nota_final > 8"; 
                tituloReporte = "8. Cuadro de Honor: Alumnos de Excelencia"; break;
            case 9: 
                consultaSql = "SELECT c.nombre, COUNT(i.id_usuario) as inscritos FROM cursos c LEFT JOIN inscripciones i ON c.id_curso = i.id_curso GROUP BY c.id_curso"; 
                tituloReporte = "9. Ocupación Actual por Curso"; break;
            case 10: 
                consultaSql = "SELECT c.nombre as Curso, a.nombre as Aula FROM cursos c JOIN aulas a ON c.id_aula = a.id_aula"; 
                tituloReporte = "10. Ubicación Física (Asignación Curso y Aula)"; break;
            case 11: 
                consultaSql = "SELECT AVG(horas) as promedio_horas FROM cursos"; 
                tituloReporte = "11. Promedio de Horas Lectivas del Centro"; break;
            case 12: 
                consultaSql = "SELECT SUM(capacidad) as capacidad_total FROM aulas"; 
                tituloReporte = "12. Capacidad Total Instalada (Aforo)"; break;
            case 13: 
                consultaSql = "SELECT nombre, email FROM usuarios WHERE email LIKE '%@lortu.eus'"; 
                tituloReporte = "13. Cuentas de Correo Corporativas"; break;
            case 14: 
                consultaSql = "SELECT * FROM inscripciones WHERE fecha_reserva = CURRENT_DATE"; 
                tituloReporte = "14. Registro de Actividad de Hoy"; break;
            case 15: 
                consultaSql = "SELECT u.nombre, c.nombre FROM usuarios u JOIN inscripciones i ON u.id_usuario = i.id_usuario JOIN cursos c ON i.id_curso = c.id_curso WHERE i.nota_final IS NULL"; 
                tituloReporte = "15. Pendientes de Calificar (Actas abiertas)"; break;
            case 16: 
                consultaSql = "SELECT c.nombre FROM cursos c JOIN aulas a ON c.id_aula = a.id_aula WHERE a.nombre = 'Aula 101'"; 
                tituloReporte = "16. Agenda Académica: Aula 101"; break;
            case 17: 
                consultaSql = "SELECT u.nombre as Alumno, c.nombre as Curso, i.nota_final FROM usuarios u JOIN inscripciones i ON u.id_usuario = i.id_usuario JOIN cursos c ON i.id_curso = c.id_curso"; 
                tituloReporte = "17. Historial Académico Completo"; break;
            case 18: 
                consultaSql = "SELECT COUNT(*) as total FROM inscripciones"; 
                tituloReporte = "18. Volumen Total de Reservas Global"; break;
            case 19: 
                consultaSql = "SELECT nombre, plazas_max FROM cursos WHERE plazas_max < 15"; 
                tituloReporte = "19. Oferta de Grupos Reducidos (Menos de 15 plazas)"; break;
            case 20: 
                consultaSql = "SELECT u.nombre, i.nota_final FROM usuarios u JOIN inscripciones i ON u.id_usuario = i.id_usuario WHERE i.nota_final IS NOT NULL ORDER BY i.nota_final DESC"; 
                tituloReporte = "20. Ranking Global de Calificaciones"; break;
            default: return "<h1>Error en la selección del informe</h1>";
        }

        StringBuilder constructorTabla = new StringBuilder("<!DOCTYPE html><html lang='es'><head><meta charset='UTF-8'><link rel='stylesheet' href='/css/admin.css'></head><body><div class='admin-container' style='padding:50px;'><h1>").append(tituloReporte).append("</h1><table border='1' style='width:100%; border-collapse:collapse;'>");

        try (Connection conexion = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement estado = conexion.createStatement();
             ResultSet resultado = estado.executeQuery(consultaSql)) {
            
            ResultSetMetaData metadatos = resultado.getMetaData();
            int columnas = metadatos.getColumnCount();
            
            constructorTabla.append("<tr style='background:#1a1a1a; color:white;'>");
            for (int i = 1; i <= columnas; i++) {
                constructorTabla.append("<th style='padding:10px;'>").append(metadatos.getColumnName(i).toUpperCase()).append("</th>");
            }
            constructorTabla.append("</tr>");
            
            while (resultado.next()) {
                constructorTabla.append("<tr>");
                for (int i = 1; i <= columnas; i++) {
                    constructorTabla.append("<td style='padding:10px;'>").append(resultado.getString(i)).append("</td>");
                }
                constructorTabla.append("</tr>");
            }
        } catch (SQLException e) { return "Error SQL al generar informe: " + e.getMessage(); }
        
        constructorTabla.append("</table><br><a href='/html/admin.html' style='display:inline-block; padding:10px 20px; background:#333; color:white; text-decoration:none;'>Volver al Panel</a></div></body></html>");
        return constructorTabla.toString();
    }
}