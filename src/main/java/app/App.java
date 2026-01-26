package app;

import static spark.Spark.*;
import java.sql.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class App {

    // --- 1. CONFIGURACIÓN DE CONEXIÓN (AWS) ---
    private static final String DB_URL = "jdbc:mysql://3.227.10.6:3306/lortu_db?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "admin_lortu";
    private static final String DB_PASS = "Lortu2026!";

    public static void main(String[] args) {
        // Configuración del servidor
        port(4567);
        staticFiles.location("/public"); 

        // --- 2. RUTAS DINÁMICAS (REQUISITO PROFESOR JAVA) ---

        // Esta ruta intercepta la petición del catálogo de cursos para inyectar los datos de la BD
        get("/html/cursoswp.html", (req, res) -> {
            String path = "src/main/resources/public/html/cursoswp.html";
            try {
                // Leemos el archivo HTML como texto
                String contenidoHtml = new String(Files.readAllBytes(Paths.get(path)));

                // REEMPLAZO DE PLAZAS: Buscamos las marcas y ponemos los COUNT de SQL
                contenidoHtml = contenidoHtml.replace("TOTAL_1", String.valueOf(obtenerContadorInscritos(1)));
                contenidoHtml = contenidoHtml.replace("TOTAL_2", String.valueOf(obtenerContadorInscritos(2)));
                contenidoHtml = contenidoHtml.replace("TOTAL_3", String.valueOf(obtenerContadorInscritos(3)));
                contenidoHtml = contenidoHtml.replace("TOTAL_4", String.valueOf(obtenerContadorInscritos(4)));

                res.type("text/html");
                return contenidoHtml;
            } catch (Exception e) {
                res.status(500);
                return "Error al cargar el catálogo dinámico: " + e.getMessage();
            }
        });

        // --- 3. RUTAS DE NAVEGACIÓN Y PROCESOS ---

        // Redirección inicial
        get("/", (req, res) -> {
            res.redirect("/html/login.html");
            return null;
        });

        // LOGIN CON CONTROL DE ROLES
        post("/login", (req, res) -> {
            String correo = req.queryParams("email");
            String clave = req.queryParams("password");

            Usuario user = validarUsuario(correo, clave);

            if (user == null) {
                res.status(401); 
                return "<h1>Error: Datos incorrectos</h1><p><a href='/html/login.html'>Volver a intentar</a></p>";
            }

            // Si es ADMIN, va al panel de gestión (web1), si es ALUMNO al index
            if (user.getRol().equalsIgnoreCase("admin")) {
                res.redirect("/html/admin.html?usuario=" + user.getNombre());
            } else {
                res.redirect("/html/index.html");
            }
            return null;
        });

        // REGISTRO DE NUEVOS USUARIOS
        post("/registro", (req, res) -> {
            String nombre = req.queryParams("nombre");
            String email = req.queryParams("email");
            String pass = req.queryParams("password");

            if (registrarUsuario(nombre, email, pass)) {
                res.redirect("/html/login.html");
            } else {
                return "<h1>Error al registrar</h1><p>El email ya existe.</p><a href='/html/registro.html'>Volver</a>";
            }
            return null;
        });

        // INSCRIPCIÓN A UN CURSO
        post("/inscribir", (req, res) -> {
            String email = req.queryParams("email");
            int idCurso = Integer.parseInt(req.queryParams("id_curso"));

            if (inscribirAlumno(email, idCurso)) {
                res.type("text/html");
                return """
                    <body style='text-align:center; padding:50px; font-family:sans-serif;'>
                        <h1 style='color:green;'>Inscripción Correcta</h1>
                        <p>Se ha guardado en la base de datos de AWS.</p>
                        <a href='/html/index.html'>Volver al Inicio</a>
                    </body>
                """;
            } else {
                return "<h1>Error</h1><p>No se pudo procesar la inscripción.</p><a href='/html/cursoswp.html'>Volver</a>";
            }
        });

        // --- 4. RUTAS PARA INFORMES (REQUISITO BBDD - 20 CONSULTAS) ---
        
        // Esta ruta dinámica maneja los informes del Admin
        get("/admin/consulta/:id", (req, res) -> {
            int idConsulta = Integer.parseInt(req.params(":id"));
            res.type("text/html");
            return ejecutarConsultaAdmin(idConsulta);
        });
    }

    // --- 5. MÉTODOS DE ACCESO A DATOS (SQL) ---

    // Cuenta cuántos alumnos hay en un curso (Para el profesor de Java)
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

    // Valida credenciales y devuelve el objeto Usuario con su ROL
    private static Usuario validarUsuario(String email, String pass) {
        String sql = "SELECT id_usuario, nombre, email, password, rol FROM usuarios WHERE email = ? AND password = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, pass);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Usuario(
                        rs.getInt("id_usuario"),
                        rs.getString("nombre"),
                        rs.getString("email"),
                        rs.getString("password"),
                        rs.getString("rol")
                    );
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

    // --- 6. MOTOR DE INFORMES (LAS 20 CONSULTAS) ---
    private static String ejecutarConsultaAdmin(int num) {
        String sql = "";
        String titulo = "";
        
        // Definimos la consulta según el botón pulsado en admin.html
        switch(num) {
            case 1: sql = "SELECT nombre, email FROM usuarios WHERE rol = 'alumno'"; titulo = "Lista de Alumnos"; break;
            case 2: sql = "SELECT nombre, horas FROM cursos WHERE subvencionado = 1"; titulo = "Cursos Gratuitos"; break;
            case 3: sql = "SELECT u.nombre, c.nombre AS curso FROM usuarios u JOIN inscripciones i ON u.id_usuario = i.id_usuario JOIN cursos c ON i.id_curso = c.id_curso"; titulo = "Informe de Inscripciones Completo"; break;
            // Aquí añadirías los casos hasta el 20...
            default: return "Consulta no implementada.";
        }

        StringBuilder html = new StringBuilder("<h2>" + titulo + "</h2><table border='1' style='width:100%; border-collapse:collapse;'>");
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            
            ResultSetMetaData rsmd = rs.getMetaData();
            int columnas = rsmd.getColumnCount();

            // Cabecera de la tabla
            html.append("<tr style='background:#eee;'>");
            for (int i = 1; i <= columnas; i++) html.append("<th>").append(rsmd.getColumnName(i)).append("</th>");
            html.append("</tr>");

            // Datos
            while (rs.next()) {
                html.append("<tr>");
                for (int i = 1; i <= columnas; i++) html.append("<td>").append(rs.getString(i)).append("</td>");
                html.append("</tr>");
            }
        } catch (SQLException e) { return "Error SQL: " + e.getMessage(); }
        
        html.append("</table><br><a href='/html/admin.html'>Volver al Panel</a>");
        return html.toString();
    }
}