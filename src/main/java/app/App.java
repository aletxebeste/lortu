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
     * Carga la contraseña desde el archivo local db.properties
     */
    private static String obtenerPassword() {
        Properties prop = new Properties();
        try (FileInputStream fis = new FileInputStream("db.properties")) {
            prop.load(fis);
            String pass = prop.getProperty("db.password");
            if (pass != null) return pass;
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

        // Ruta para el Catálogo de Cursos (Dinámica)
        get("/html/cursoswp.html", (req, res) -> {
            String path = "src/main/resources/public/html/cursos_plantilla.html";
            try {
                byte[] bytes = Files.readAllBytes(Paths.get(path));
                String contenidoHtml = new String(bytes, "UTF-8");

                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                    for (int i = 1; i <= 4; i++) {
                        int maxPlazas = obtenerPlazasMax(conn, i);
                        int inscritos = obtenerContadorInscritos(conn, i);
                        int disponibles = maxPlazas - inscritos;
                        
                        contenidoHtml = contenidoHtml.replace("TOTAL_" + i, String.valueOf(maxPlazas));
                        contenidoHtml = contenidoHtml.replace("DISPONIBLES_" + i, String.valueOf(disponibles));
                    }
                }

                res.type("text/html; charset=UTF-8");
                return contenidoHtml;
            } catch (Exception e) {
                res.status(500);
                return "Error crítico al cargar el catálogo: " + e.getMessage();
            }
        });

        // RUTA MODIFICADA: Panel de Administración Dinámico
        // Ahora busca el archivo directamente en src/main/resources/admin.html
        get("/html/admin.html", (req, res) -> {
            // HEMOS CAMBIADO ESTA RUTA PARA QUE COINCIDA CON EL MOVIMIENTO DEL ARCHIVO
            String path = "src/main/resources/admin.html"; 
            try {
                byte[] bytes = Files.readAllBytes(Paths.get(path));
                String contenidoHtml = new String(bytes, "UTF-8");

                // Generamos el HTML de las opciones de los cursos desde AWS
                String opcionesCursos = obtenerHtmlOpcionesCursos();

                // Reemplazamos el marcador dinámico por la lista de cursos de la BD
                contenidoHtml = contenidoHtml.replace("{{OPCIONES_CURSOS}}", opcionesCursos);

                res.type("text/html; charset=UTF-8");
                return contenidoHtml;
            } catch (Exception e) {
                res.status(500);
                return "Error al procesar el panel de administración: " + e.getMessage() + 
                       " (Buscando en: " + Paths.get(path).toAbsolutePath() + ")";
            }
        });

        // --- 3. PROCESOS DE USUARIO (LOGIN, REGISTRO, INSCRIPCIÓN) ---

        post("/login", (req, res) -> {
            String correo = req.queryParams("email");
            String clave = req.queryParams("password");
            Usuario user = validarUsuario(correo, clave);

            if (user == null) {
                res.status(401); 
                return "<html><head><meta charset='UTF-8'></head><body style='font-family:sans-serif; text-align:center; padding-top:50px;'>" +
                       "<h1 style='color:red;'>Acceso Denegado</h1>" +
                       "<p>Correo o contraseña incorrectos.</p>" +
                       "<a href='/html/login.html'>Volver a intentar</a></body></html>";
            }

            if (user.getRol().equalsIgnoreCase("admin")) {
                res.redirect("/html/admin.html");
            } else {
                res.redirect("/html/index.html");
            }
            return null;
        });

        post("/registro", (req, res) -> {
            String nombre = req.queryParams("nombre");
            String email = req.queryParams("email");
            String pass = req.queryParams("password");
            if (registrarUsuario(nombre, email, pass)) {
                res.redirect("/html/login.html");
            } else {
                return "<h1>Error</h1><p>Email en uso.</p><a href='/html/registro.html'>Volver</a>";
            }
            return null;
        });

        post("/inscribir", (req, res) -> {
            String email = req.queryParams("email");
            int idCurso = Integer.parseInt(req.queryParams("id_curso"));

            if (!existeUsuario(email)) {
                res.type("text/html; charset=UTF-8");
                return "<html><body><h1 style='color:orange;'>Usuario no encontrado</h1>" +
                       "<p>El email <b>" + email + "</b> no está registrado en LORTU.</p>" +
                       "<a href='/html/registro.html'>Ir a Registro</a></body></html>";
            }

            if (inscribirAlumno(email, idCurso)) {
                res.type("text/html; charset=UTF-8");
                return "<body><h1 style='color:green;'>Inscripción Exitosa</h1><a href='/html/index.html'>Volver</a></body>";
            } else {
                return "<h1>Error</h1><p>Hubo un problema al procesar tu inscripción.</p>";
            }
        });

        // --- 4. RUTAS DE ADMINISTRACIÓN INTERACTIVA ---

        get("/admin/alumnos_curso", (req, res) -> {
            String idCurso = req.queryParams("id");
            if (idCurso == null || idCurso.isEmpty()) return "ID de curso no proporcionado.";

            StringBuilder html = new StringBuilder("<!DOCTYPE html><html lang='es'><head><meta charset='UTF-8'><link rel='stylesheet' href='/css/admin.css'></head><body>");
            html.append("<div class='admin-container' style='padding:50px;'><h1>Alumnos inscritos en Curso seleccionado:</h1>");
            html.append("<table border='1' style='width:100%; border-collapse:collapse;'><tr style='background:#ffa500; color:white;'><th>Nombre Alumno</th><th>Email</th></tr>");

            String sql = "SELECT u.nombre, u.email FROM usuarios u JOIN inscripciones i ON u.id_usuario = i.id_usuario WHERE i.id_curso = ?";

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, Integer.parseInt(idCurso));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        html.append("<tr><td style='padding:10px;'>").append(rs.getString("nombre"))
                            .append("</td><td style='padding:10px;'>").append(rs.getString("email")).append("</td></tr>");
                    }
                }
            } catch (SQLException e) { return "Error SQL: " + e.getMessage(); }

            html.append("</table><br><a href='/html/admin.html' style='padding:10px; background:#333; color:white; text-decoration:none;'>Volver al Panel</a></div></body></html>");
            res.type("text/html; charset=UTF-8");
            return html.toString();
        });

        post("/admin/actualizar_curso", (req, res) -> {
            int id = Integer.parseInt(req.queryParams("id"));
            int plazas = Integer.parseInt(req.queryParams("plazas"));
            int horas = Integer.parseInt(req.queryParams("horas"));

            String sql = "UPDATE cursos SET plazas_max = ?, horas = ? WHERE id_curso = ?";

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, plazas);
                ps.setInt(2, horas);
                ps.setInt(3, id);
                
                int actualizados = ps.executeUpdate();
                res.type("text/html; charset=UTF-8");
                if (actualizados > 0) {
                    return "<html><body><h2 style='color:green;'>✅ Base de Datos AWS Actualizada</h2><p>Los cambios se han aplicado correctamente.</p><a href='/html/admin.html'>Volver al Panel</a></body></html>";
                } else {
                    return "<h2>❌ No se pudo actualizar el curso</h2><a href='/html/admin.html'>Volver</a>";
                }
            } catch (SQLException e) { return "Error al conectar con AWS: " + e.getMessage(); }
        });

        // --- 5. RUTA INFORMES ADMIN (LOS 20 INFORMES FIJOS) ---
        get("/admin/consulta/:id", (req, res) -> {
            int idConsulta = Integer.parseInt(req.params(":id"));
            res.type("text/html; charset=UTF-8"); 
            return ejecutarConsultaAdmin(idConsulta);
        });
    }

    // --- 6. MÉTODOS DE APOYO Y ACCESO A DATOS (JDBC) ---

    private static String obtenerHtmlOpcionesCursos() {
        StringBuilder opciones = new StringBuilder();
        String sql = "SELECT id_curso, nombre FROM cursos ORDER BY nombre ASC";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            
            while (rs.next()) {
                int id = rs.getInt("id_curso");
                String nombre = rs.getString("nombre");
                opciones.append("<option value='").append(id).append("'>")
                        .append(nombre).append("</option>");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "<option>Error al cargar cursos de AWS</option>";
        }
        return opciones.toString();
    }

    private static boolean existeUsuario(String email) {
        String sql = "SELECT COUNT(*) AS total FROM usuarios WHERE email = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("total") > 0;
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    private static int obtenerContadorInscritos(Connection conn, int idCurso) {
        String sql = "SELECT COUNT(*) AS total FROM inscripciones WHERE id_curso = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCurso);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("total");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    private static int obtenerPlazasMax(Connection conn, int idCurso) {
        String sql = "SELECT plazas_max FROM cursos WHERE id_curso = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCurso);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("plazas_max");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    private static Usuario validarUsuario(String email, String pass) {
        String sql = "SELECT id_usuario, nombre, email, password, rol FROM usuarios WHERE email = ? AND password = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, pass);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Usuario(rs.getInt("id_usuario"), rs.getString("nombre"), rs.getString("email"), rs.getString("password"), rs.getString("rol"));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    private static boolean registrarUsuario(String nombre, String email, String pass) {
        String sql = "INSERT INTO usuarios (nombre, email, password, rol) VALUES (?, ?, ?, 'alumno')";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nombre);
            ps.setString(2, email);
            ps.setString(3, pass);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    private static boolean inscribirAlumno(String emailAlumno, int idCurso) {
        String sql = "INSERT INTO inscripciones (id_usuario, id_curso, fecha_reserva, estado) " +
                     "VALUES ((SELECT id_usuario FROM usuarios WHERE email = ?), ?, CURRENT_DATE, 'Confirmada')";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, emailAlumno);
            ps.setInt(2, idCurso);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    // --- 7. MOTOR DE INFORMES ADMIN (SWITCH) ---

    private static String ejecutarConsultaAdmin(int num) {
        String sql = "";
        String titulo = "";
        
        switch(num) {
            case 1: sql = "SELECT nombre, email FROM usuarios WHERE rol = 'alumno'"; titulo = "Lista de Alumnos"; break;
            case 2: sql = "SELECT nombre, horas, plazas_max FROM cursos"; titulo = "Catálogo de Cursos"; break;
            case 3: sql = "SELECT * FROM aulas"; titulo = "Infraestructura: Aulas"; break;
            case 4: sql = "SELECT nombre, email FROM usuarios WHERE rol = 'admin'"; titulo = "Equipo de Administración"; break;
            case 5: sql = "SELECT nombre, horas FROM cursos WHERE horas > 80"; titulo = "Cursos Intensivos (>80h)"; break;
            case 6: sql = "SELECT nombre FROM cursos WHERE subvencionado = 1"; titulo = "Cursos Subvencionados"; break;
            case 7: sql = "SELECT * FROM inscripciones WHERE estado = 'Confirmada'"; titulo = "Inscripciones Validadas"; break;
            case 8: sql = "SELECT u.nombre, i.nota_final FROM usuarios u JOIN inscripciones i ON u.id_usuario = i.id_usuario WHERE i.nota_final > 8"; titulo = "Alumnos de Excelencia"; break;
            case 9: sql = "SELECT c.nombre, COUNT(i.id_usuario) as inscritos FROM cursos c LEFT JOIN inscripciones i ON c.id_curso = i.id_curso GROUP BY c.nombre"; titulo = "Ocupación por Curso"; break;
            case 10: sql = "SELECT c.nombre as Curso, a.nombre as Aula FROM cursos c JOIN aulas a ON c.id_aula = a.id_aula"; titulo = "Ubicación de Cursos"; break;
            case 11: sql = "SELECT AVG(horas) as Media FROM cursos"; titulo = "Promedio de Horas Lectivas"; break;
            case 12: sql = "SELECT SUM(capacidad) as Total FROM aulas"; titulo = "Capacidad Total Instalada"; break;
            case 13: sql = "SELECT nombre, email FROM usuarios WHERE email LIKE '%@lortu.eus'"; titulo = "Cuentas Corporativas"; break;
            case 14: sql = "SELECT * FROM inscripciones WHERE fecha_reserva = CURRENT_DATE"; titulo = "Actividad de Hoy"; break;
            case 15: sql = "SELECT u.nombre, c.nombre FROM usuarios u JOIN inscripciones i ON u.id_usuario = i.id_usuario JOIN cursos c ON i.id_curso = c.id_curso WHERE i.nota_final IS NULL"; titulo = "Pendientes de Calificar"; break;
            case 16: sql = "SELECT c.nombre FROM cursos c JOIN aulas a ON c.id_aula = a.id_aula WHERE a.nombre = 'Aula 101'"; titulo = "Agenda Aula 101"; break;
            case 17: sql = "SELECT u.nombre as Alumno, c.nombre as Curso, i.nota_final FROM usuarios u JOIN inscripciones i ON u.id_usuario = i.id_usuario JOIN cursos c ON i.id_curso = c.id_curso"; titulo = "Registro Histórico Full"; break;
            case 18: sql = "SELECT COUNT(*) as Total FROM inscripciones"; titulo = "Volumen Total de Reservas"; break;
            case 19: sql = "SELECT nombre, plazas_max FROM cursos WHERE plazas_max < 15"; titulo = "Cursos de Grupo Reducido"; break;
            case 20: sql = "SELECT u.nombre, i.nota_final FROM usuarios u JOIN inscripciones i ON u.id_usuario = i.id_usuario WHERE i.nota_final IS NOT NULL ORDER BY i.nota_final DESC"; titulo = "Ranking de Calificaciones"; break;
            default: return "<html><body><h1>Error</h1></body></html>";
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang='es'><head><meta charset='UTF-8'><link rel='stylesheet' href='/css/admin.css'></head><body>");
        html.append("<div class='admin-container' style='padding:50px;'><h1>").append(titulo).append("</h1><table border='1' style='width:100%; border-collapse:collapse;'>");

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData rsmd = rs.getMetaData();
            int col = rsmd.getColumnCount();
            html.append("<tr style='background:#1a1a1a; color:white;'>");
            for (int i = 1; i <= col; i++) html.append("<th style='padding:10px;'>").append(rsmd.getColumnName(i).toUpperCase()).append("</th>");
            html.append("</tr>");
            while (rs.next()) {
                html.append("<tr>");
                for (int i = 1; i <= col; i++) html.append("<td style='padding:10px;'>").append(rs.getString(i)).append("</td>");
                html.append("</tr>");
            }
        } catch (SQLException e) { return "Error SQL: " + e.getMessage(); }
        
        html.append("</table><br><a href='/html/admin.html' style='display:inline-block; padding:10px 20px; background:#1a1a1a; color:white; text-decoration:none; border-radius:5px;'>Volver al Panel</a></div></body></html>");
        return html.toString();
    }
}