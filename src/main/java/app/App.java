package app;

import static spark.Spark.*;
import java.sql.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.IOException;

public class App {

    // --- 1. CONFIGURACIÓN DE CONEXIÓN (AMAZON WEB SERVICES) ---
    private static final String DB_URL = "jdbc:mysql://18.206.19.232:3306/lortu_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
    private static final String DB_USER = "admin_lortu";
    private static final String DB_PASS = obtenerPassword();

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
                return "<html><head><meta charset='UTF-8'></head><body><h1>Acceso Denegado</h1><p>Correo o contraseña incorrectos.</p><a href='/html/login.html'>Volver</a></body></html>";
            }

            if (objetoUsuario.getRol().equalsIgnoreCase("admin")) {
                res.redirect("/html/admin.html");
            } else {
                res.redirect("/html/index.html");
            }
            return null;
        });

        post("/registro", (req, res) -> {
            String nombreIngresado = req.queryParams("nombre");
            String emailIngresado = req.queryParams("email");
            String passwordIngresado = req.queryParams("password");

            // USO DEL MÉTODO existeUsuario PARA VALIDAR DUPLICADOS
            if (existeUsuario(emailIngresado)) {
                res.type("text/html; charset=UTF-8");
                return "<html><body><h1>Error</h1><p>El correo electrónico ya está registrado.</p><a href='/html/registro.html'>Intentar de nuevo</a></body></html>";
            }

            if (registrarUsuario(nombreIngresado, emailIngresado, passwordIngresado)) {
                res.redirect("/html/login.html");
            } else {
                return "<h1>Error</h1><p>Hubo un fallo en el servidor durante el registro.</p>";
            }
            return null;
        });

        post("/inscribir", (req, res) -> {
            String emailAlumno = req.queryParams("email");
            String idCursoTexto = req.queryParams("id_curso");
            int idDelCurso = Integer.parseInt(idCursoTexto);

            // USO DEL MÉTODO existeUsuario PARA VALIDAR QUE EL ALUMNO ES REAL
            if (!existeUsuario(emailAlumno)) {
                res.type("text/html; charset=UTF-8");
                return "<html><body><h1>Usuario No Encontrado</h1><p>El email introducido no pertenece a ningún usuario de LORTU.</p><a href='/html/index.html'>Volver</a></body></html>";
            }

            if (inscribirAlumno(emailAlumno, idDelCurso)) {
                res.type("text/html; charset=UTF-8");
                return "<body><h1 style='color:green;'>Inscripción Realizada con Éxito</h1><a href='/html/index.html'>Volver a la página principal</a></body>";
            } else {
                return "<h1>Error</h1><p>Hubo un problema al procesar tu inscripción en Amazon Web Services.</p>";
            }
        });

        // --- 4. RUTAS DE ADMINISTRACIÓN INTERACTIVA ---

        get("/admin/alumnos_curso", (req, res) -> {
            String idDelCurso = req.queryParams("id");
            StringBuilder constructorHtml = new StringBuilder("<!DOCTYPE html><html lang='es'><head><meta charset='UTF-8'><link rel='stylesheet' href='/css/admin.css'></head><body><div class='admin-container' style='padding:50px;'><h1>Alumnos inscritos en el curso seleccionado:</h1><table border='1' style='width:100%; border-collapse:collapse;'>");
            constructorHtml.append("<tr style='background:#ffa500; color:white;'><th>Nombre Alumno</th><th>Correo Electrónico</th></tr>");

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

            constructorHtml.append("</table><br><a href='/html/admin.html'>Volver al Panel de Administración</a></div></body></html>");
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
                res.type("text/html; charset=UTF-8");
                return "<html><body><h2 style='color:green;'>✅ Parámetros de Curso Actualizados en AWS</h2><a href='/html/admin.html'>Volver al Panel</a></body></html>";
            } catch (SQLException e) { return "Error al conectar con AWS: " + e.getMessage(); }
        });

        get("/admin/consulta/:id", (req, res) -> {
            int idDeLaConsulta = Integer.parseInt(req.params(":id"));
            res.type("text/html; charset=UTF-8"); 
            return ejecutarConsultaAdmin(idDeLaConsulta);
        });
    }

    // --- 5. MÉTODOS DE APOYO JDBC (SIN ABREVIATURAS) ---

    private static String obtenerNombreCurso(Connection conexion, int idCurso) {
        String sql = "SELECT nombre FROM cursos WHERE id_curso = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, idCurso);
            try (ResultSet resultado = sentencia.executeQuery()) {
                if (resultado.next()) return resultado.getString("nombre");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return "Curso No Encontrado";
    }

    private static int obtenerHorasCurso(Connection conexion, int idCurso) {
        String sql = "SELECT horas FROM cursos WHERE id_curso = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, idCurso);
            try (ResultSet resultado = sentencia.executeQuery()) {
                if (resultado.next()) return resultado.getInt("horas");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    private static int obtenerPlazasMax(Connection conexion, int idCurso) {
        String sql = "SELECT plazas_max FROM cursos WHERE id_curso = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, idCurso);
            try (ResultSet resultado = sentencia.executeQuery()) {
                if (resultado.next()) return resultado.getInt("plazas_max");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    private static int obtenerContadorInscritos(Connection conexion, int idCurso) {
        String sql = "SELECT COUNT(*) FROM inscripciones WHERE id_curso = ?";
        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setInt(1, idCurso);
            try (ResultSet resultado = sentencia.executeQuery()) {
                if (resultado.next()) return resultado.getInt(1);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    private static String obtenerHtmlOpcionesCursos() {
        StringBuilder opcionesHtml = new StringBuilder();
        try (Connection conexion = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement estado = conexion.createStatement();
             ResultSet resultado = estado.executeQuery("SELECT id_curso, nombre FROM cursos ORDER BY nombre ASC")) {
            while (resultado.next()) {
                opcionesHtml.append("<option value='").append(resultado.getInt("id_curso")).append("'>")
                        .append(resultado.getString("nombre")).append("</option>");
            }
        } catch (SQLException e) { return "<option>Error al cargar catálogo</option>"; }
        return opcionesHtml.toString();
    }

    private static Usuario validarUsuario(String correoElectronico, String passwordUsuario) {
        String sql = "SELECT id_usuario, nombre, email, password, rol FROM usuarios WHERE email = ? AND password = ?";
        try (Connection conexion = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, correoElectronico);
            sentencia.setString(2, passwordUsuario);
            try (ResultSet resultado = sentencia.executeQuery()) {
                if (resultado.next()) return new Usuario(resultado.getInt(1), resultado.getString(2), resultado.getString(3), resultado.getString(4), resultado.getString(5));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    private static boolean registrarUsuario(String nombreCompleto, String correoElectronico, String passwordUsuario) {
        String sql = "INSERT INTO usuarios (nombre, email, password, rol) VALUES (?, ?, ?, 'alumno')";
        try (Connection conexion = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS); 
             PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, nombreCompleto); 
            sentencia.setString(2, correoElectronico); 
            sentencia.setString(3, passwordUsuario); 
            return sentencia.executeUpdate() > 0;
        } catch (Exception e) { return false; }
    }

    private static boolean inscribirAlumno(String correoAlumno, int idCurso) {
        String sql = "INSERT INTO inscripciones (id_usuario, id_curso, fecha_reserva, estado) VALUES ((SELECT id_usuario FROM usuarios WHERE email = ?), ?, CURRENT_DATE, 'Confirmada')";
        try (Connection conexion = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS); 
             PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, correoAlumno); 
            sentencia.setInt(2, idCurso); 
            return sentencia.executeUpdate() > 0;
        } catch (Exception e) { return false; }
    }

    // --- EL MÉTODO QUE SALÍA EN AMARILLO AHORA SE USA PARA SEGURIDAD ---
    private static boolean existeUsuario(String correoElectronico) {
        String sql = "SELECT id_usuario FROM usuarios WHERE email = ?";
        try (Connection conexion = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS); 
             PreparedStatement sentencia = conexion.prepareStatement(sql)) {
            sentencia.setString(1, correoElectronico); 
            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next(); // Retorna true si encuentra al usuario
            }
        } catch (Exception e) { return false; }
    }

    // --- 6. MOTOR DE INFORMES SINCRONIZADO CON ADMIN.HTML ---

    private static String ejecutarConsultaAdmin(int numeroConsulta) {
        String consultaSql = "";
        String tituloReporte = "";
        
        switch(numeroConsulta) {
            case 1: consultaSql = "SELECT nombre, email FROM usuarios WHERE rol = 'alumno' ORDER BY nombre"; tituloReporte = "1. Listado Oficial de Alumnos"; break;
            case 2: consultaSql = "SELECT nombre, horas, plazas_max FROM cursos ORDER BY nombre"; tituloReporte = "2. Catálogo Vigente de Cursos"; break;
            case 3: consultaSql = "SELECT * FROM aulas"; tituloReporte = "3. Gestión de Infraestructura: Aulas"; break;
            case 4: consultaSql = "SELECT nombre, email FROM usuarios WHERE rol = 'admin'"; tituloReporte = "4. Administradores del Sistema"; break;
            case 5: consultaSql = "SELECT nombre, horas FROM cursos WHERE horas > 80"; tituloReporte = "5. Cursos de Larga Duración (Mas de 80 horas)"; break;
            case 6: consultaSql = "SELECT nombre FROM cursos WHERE subvencionado = 1"; tituloReporte = "6. Cursos con Subvención Activa"; break;
            case 7: consultaSql = "SELECT * FROM inscripciones WHERE estado = 'Confirmada'"; tituloReporte = "7. Inscripciones Confirmadas"; break;
            case 8: consultaSql = "SELECT u.nombre, i.nota_final FROM usuarios u JOIN inscripciones i ON u.id_usuario = i.id_usuario WHERE i.nota_final > 8"; tituloReporte = "8. Cuadro de Honor: Alumnos de Excelencia"; break;
            case 9: consultaSql = "SELECT c.nombre, COUNT(i.id_usuario) as inscritos FROM cursos c LEFT JOIN inscripciones i ON c.id_curso = i.id_curso GROUP BY c.id_curso, c.nombre"; tituloReporte = "9. Ocupación Actual por Curso"; break;
            case 10: consultaSql = "SELECT c.nombre as Curso, a.nombre as Aula FROM cursos c JOIN aulas a ON c.id_aula = a.id_aula"; tituloReporte = "10. Ubicación Física (Asignación Curso y Aula)"; break;
            case 11: consultaSql = "SELECT AVG(horas) as promedio FROM cursos"; tituloReporte = "11. Promedio de Horas Lectivas del Centro"; break;
            case 12: consultaSql = "SELECT SUM(capacidad) as total FROM aulas"; tituloReporte = "12. Capacidad Total Instalada (Aforo)"; break;
            case 13: consultaSql = "SELECT nombre, email FROM usuarios WHERE email LIKE '%@lortu.eus'"; tituloReporte = "13. Cuentas de Correo Corporativas"; break;
            case 14: consultaSql = "SELECT * FROM inscripciones WHERE fecha_reserva = CURRENT_DATE"; tituloReporte = "14. Registro de Matriculaciones de Hoy"; break;
            case 15: consultaSql = "SELECT u.nombre, c.nombre FROM usuarios u JOIN inscripciones i ON u.id_usuario = i.id_usuario JOIN cursos c ON i.id_curso = c.id_curso WHERE i.nota_final IS NULL"; tituloReporte = "15. Pendientes de Calificar (Actas abiertas)"; break;
            case 16: consultaSql = "SELECT c.nombre FROM cursos c JOIN aulas a ON c.id_aula = a.id_aula WHERE a.nombre = 'Aula 101'"; tituloReporte = "16. Agenda Académica: Aula 101"; break;
            case 17: consultaSql = "SELECT u.nombre as Alumno, c.nombre as Curso, i.nota_final FROM usuarios u JOIN inscripciones i ON u.id_usuario = i.id_usuario JOIN cursos c ON i.id_curso = c.id_curso"; tituloReporte = "17. Historial Académico Completo"; break;
            case 18: consultaSql = "SELECT COUNT(*) as total FROM inscripciones"; tituloReporte = "18. Volumen Total de Reservas Global"; break;
            case 19: consultaSql = "SELECT nombre, plazas_max FROM cursos WHERE plazas_max < 15"; tituloReporte = "19. Oferta de Grupos Reducidos (Menos de 15 plazas)"; break;
            case 20: consultaSql = "SELECT u.nombre, i.nota_final FROM usuarios u JOIN inscripciones i ON u.id_usuario = i.id_usuario WHERE i.nota_final IS NOT NULL ORDER BY i.nota_final DESC"; tituloReporte = "20. Ranking Global de Calificaciones"; break;
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