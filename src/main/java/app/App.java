package app;

import static spark.Spark.*;
import java.sql.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class App {

    // --- 1. CONFIGURACIÓN DE CONEXIÓN (AWS) ---
    // Se añade characterEncoding=UTF-8 para evitar problemas con tildes y eñes
    private static final String DB_URL = "jdbc:mysql://18.206.19.232:3306/lortu_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
    private static final String DB_USER = "admin_lortu";
    private static final String DB_PASS = "Lortu2026!";

    public static void main(String[] args) {
        // Configuración del puerto y carpeta de archivos estáticos
        port(4567);
        staticFiles.location("/public"); 

        // --- 2. RUTAS DINÁMICAS ---

        // Ruta para el catálogo: Inyecta el conteo de inscritos desde AWS en el HTML
        get("/html/cursoswp.html", (req, res) -> {
            String path = "src/main/resources/public/html/cursoswp.html";
            try {
                String contenidoHtml = new String(Files.readAllBytes(Paths.get(path)));

                // Reemplazo de marcas de texto por datos reales de la BD
                contenidoHtml = contenidoHtml.replace("TOTAL_1", String.valueOf(obtenerContadorInscritos(1)));
                contenidoHtml = contenidoHtml.replace("TOTAL_2", String.valueOf(obtenerContadorInscritos(2)));
                contenidoHtml = contenidoHtml.replace("TOTAL_3", String.valueOf(obtenerContadorInscritos(3)));
                contenidoHtml = contenidoHtml.replace("TOTAL_4", String.valueOf(obtenerContadorInscritos(4)));

                res.type("text/html; charset=UTF-8");
                return contenidoHtml;
            } catch (Exception e) {
                res.status(500);
                return "Error al cargar el catálogo: " + e.getMessage();
            }
        });

        // Redirección inicial al Login
        get("/", (req, res) -> {
            res.redirect("/html/login.html");
            return null;
        });

        // Proceso de Login
        post("/login", (req, res) -> {
            String correo = req.queryParams("email");
            String clave = req.queryParams("password");

            Usuario user = validarUsuario(correo, clave);

            if (user == null) {
                res.status(401); 
                return "<html><body style='font-family:sans-serif; text-align:center; padding-top:50px;'>" +
                       "<h1 style='color:red;'>Error: Datos incorrectos</h1>" +
                       "<p>El correo o la contraseña no son válidos.</p>" +
                       "<a href='/html/login.html'>Volver a intentar</a></body></html>";
            }

            // Redirección según el rol del usuario
            if (user.getRol().equalsIgnoreCase("admin")) {
                res.redirect("/html/admin.html?usuario=" + user.getNombre());
            } else {
                res.redirect("/html/index.html");
            }
            return null;
        });

        // Proceso de Registro de nuevos alumnos
        post("/registro", (req, res) -> {
            String nombre = req.queryParams("nombre");
            String email = req.queryParams("email");
            String pass = req.queryParams("password");

            if (registrarUsuario(nombre, email, pass)) {
                res.redirect("/html/login.html");
            } else {
                return "<h1>Error al registrar</h1><p>Es posible que el email ya esté en uso.</p><a href='/html/registro.html'>Volver</a>";
            }
            return null;
        });

        // Proceso de Inscripción a un curso
        post("/inscribir", (req, res) -> {
            String email = req.queryParams("email");
            int idCurso = Integer.parseInt(req.queryParams("id_curso"));

            if (inscribirAlumno(email, idCurso)) {
                res.type("text/html; charset=UTF-8");
                return """
                    <body style='text-align:center; padding:50px; font-family:sans-serif;'>
                        <h1 style='color:green;'>Inscripción Correcta</h1>
                        <p>Tus datos han sido sincronizados con el servidor de AWS.</p>
                        <a href='/html/index.html' style='padding:10px 20px; background:#1a1a1a; color:white; text-decoration:none; border-radius:5px;'>Volver al Inicio</a>
                    </body>
                """;
            } else {
                return "<h1>Error</h1><p>No se pudo completar la inscripción. Verifica que tu correo esté registrado.</p><a href='/html/cursoswp.html'>Volver</a>";
            }
        });

        // Ruta para el Panel de Administrador (Ejecuta las 20 consultas)
        get("/admin/consulta/:id", (req, res) -> {
            int idConsulta = Integer.parseInt(req.params(":id"));
            res.type("text/html; charset=UTF-8");
            return ejecutarConsultaAdmin(idConsulta);
        });
    }

    // --- 3. MÉTODOS DE ACCESO A DATOS (SQL) ---

    private static int obtenerContadorInscritos(int idCurso) {
        String sql = "SELECT COUNT(*) AS total FROM inscripciones WHERE id_curso = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCurso);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("total");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    private static Usuario validarUsuario(String email, String pass) {
        System.out.println("--- INTENTO DE LOGIN ---");
        String sql = "SELECT id_usuario, nombre, email, password, rol FROM usuarios WHERE email = ? AND password = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, email);
                ps.setString(2, pass);
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("¡Acceso concedido para!: " + rs.getString("nombre"));
                        return new Usuario(
                            rs.getInt("id_usuario"),
                            rs.getString("nombre"),
                            rs.getString("email"),
                            rs.getString("password"),
                            rs.getString("rol")
                        );
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("ERROR SQL: " + e.getMessage());
        }
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

    // --- 4. MOTOR DE INFORMES (20 CONSULTAS SQL) ---

    private static String ejecutarConsultaAdmin(int num) {
        String sql = "";
        String titulo = "";
        
        switch(num) {
            case 1: sql = "SELECT nombre, email FROM usuarios WHERE rol = 'alumno'"; titulo = "Lista de Alumnos"; break;
            case 2: sql = "SELECT nombre, horas, plazas_max FROM cursos"; titulo = "Catálogo de Cursos"; break;
            case 3: sql = "SELECT * FROM aulas"; titulo = "Infraestructura: Aulas"; break;
            case 4: sql = "SELECT nombre, email FROM usuarios WHERE rol = 'admin'"; titulo = "Equipo de Administración"; break;
            case 5: sql = "SELECT nombre, horas FROM cursos WHERE horas > 80"; titulo = "Cursos Intensivos"; break;
            case 6: sql = "SELECT nombre FROM cursos WHERE subvencionado = 1"; titulo = "Cursos con Subvención"; break;
            case 7: sql = "SELECT * FROM inscripciones WHERE estado = 'Confirmada'"; titulo = "Inscripciones Validadas"; break;
            case 8: sql = "SELECT u.nombre, i.nota_final FROM usuarios u JOIN inscripciones i ON u.id_usuario = i.id_usuario WHERE i.nota_final > 8"; titulo = "Alumnos de Excelencia"; break;
            case 9: sql = "SELECT c.nombre, COUNT(i.id_usuario) as total FROM cursos c LEFT JOIN inscripciones i ON c.id_curso = i.id_curso GROUP BY c.nombre"; titulo = "Ocupación por Curso"; break;
            case 10: sql = "SELECT c.nombre as Curso, a.nombre as Aula FROM cursos c JOIN aulas a ON c.id_aula = a.id_aula"; titulo = "Ubicación de Cursos"; break;
            case 11: sql = "SELECT AVG(horas) as Media FROM cursos"; titulo = "Promedio de Horas Lectivas"; break;
            case 12: sql = "SELECT SUM(capacidad) as Total FROM aulas"; titulo = "Capacidad Total del Centro"; break;
            case 13: sql = "SELECT nombre, email FROM usuarios WHERE email LIKE '%@lortu.eus'"; titulo = "Cuentas Corporativas"; break;
            case 14: sql = "SELECT * FROM inscripciones WHERE fecha_reserva = CURRENT_DATE"; titulo = "Actividad de Hoy"; break;
            case 15: sql = "SELECT u.nombre, c.nombre FROM usuarios u JOIN inscripciones i ON u.id_usuario = i.id_usuario JOIN cursos c ON i.id_curso = c.id_curso WHERE i.nota_final IS NULL"; titulo = "Pendientes de Calificar"; break;
            case 16: sql = "SELECT c.nombre FROM cursos c JOIN aulas a ON c.id_aula = a.id_aula WHERE a.nombre = 'Aula 101'"; titulo = "Agenda Aula 101"; break;
            case 17: sql = "SELECT u.nombre, c.nombre, i.nota_final FROM usuarios u JOIN inscripciones i ON u.id_usuario = i.id_usuario JOIN cursos c ON i.id_curso = c.id_curso"; titulo = "Registro Histórico"; break;
            case 18: sql = "SELECT COUNT(*) as Total FROM inscripciones"; titulo = "Volumen Total de Reservas"; break;
            case 19: sql = "SELECT nombre, plazas_max FROM cursos WHERE plazas_max < 15"; titulo = "Cursos de Grupo Reducido"; break;
            case 20: sql = "SELECT u.nombre, i.nota_final FROM usuarios u JOIN inscripciones i ON u.id_usuario = i.id_usuario WHERE i.nota_final IS NOT NULL ORDER BY i.nota_final DESC"; titulo = "Ranking de Calificaciones"; break;
            default: return "Consulta no implementada.";
        }

        StringBuilder html = new StringBuilder();
        // Se añade meta charset UTF-8 para que el navegador muestre bien las tildes del resultado
        html.append("<!DOCTYPE html><html lang='es'><head><meta charset='UTF-8'>");
        html.append("<link rel='stylesheet' href='/css/admin.css'>");
        html.append("<title>LORTU - Informe</title></head><body>");
        html.append("<div class='admin-container' style='padding:40px; font-family:sans-serif;'>");
        html.append("<h1 style='color:#1a1a1a;'>").append(titulo).append("</h1>");
        html.append("<table border='1' style='width:100%; border-collapse:collapse; margin-top:20px;'>");

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            ResultSetMetaData rsmd = rs.getMetaData();
            int columnas = rsmd.getColumnCount();

            // Cabecera de la tabla
            html.append("<tr style='background:#1a1a1a; color:white;'>");
            for (int i = 1; i <= columnas; i++) {
                html.append("<th style='padding:12px;'>").append(rsmd.getColumnName(i).toUpperCase()).append("</th>");
            }
            html.append("</tr>");

            // Cuerpo de la tabla
            while (rs.next()) {
                html.append("<tr>");
                for (int i = 1; i <= columnas; i++) {
                    String valor = rs.getString(i);
                    html.append("<td style='padding:10px; border-bottom:1px solid #ddd;'>")
                        .append(valor == null ? "-" : valor)
                        .append("</td>");
                }
                html.append("</tr>");
            }
        } catch (SQLException e) { 
            return "<html><body><h2>Error en la consulta SQL: " + e.getMessage() + "</h2></body></html>"; 
        }
        
        html.append("</table>");
        html.append("<br><br><a href='/html/admin.html' style='display:inline-block; padding:12px 25px; background:#1a1a1a; color:white; text-decoration:none; border-radius:50px;'>Volver al Panel de Control</a>");
        html.append("</div></body></html>");
        
        return html.toString();
    }
}