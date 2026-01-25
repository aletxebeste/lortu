package app;

import static spark.Spark.*;
import java.sql.*;

public class App {

    // --- 1. CONFIGURACIÓN DE CONEXIÓN (AWS) ---
    private static final String DB_URL = "jdbc:mysql://3.227.10.6:3306/lortu_db?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "admin_lortu";
    private static final String DB_PASS = "Lortu2026!";

    public static void main(String[] args) {
        port(4567);
        // Indicamos que los archivos están en /public
        staticFiles.location("/public"); 

        // --- 2. RUTAS DE NAVEGACIÓN ---

        // Ruta Raíz -> Ahora redirige a /html/login.html
        get("/", (req, res) -> {
            res.redirect("/html/login.html");
            return null;
        });

        // PROCESO DE LOGIN
        post("/login", (req, res) -> {
            String correo = req.queryParams("email");
            String clave = req.queryParams("password");

            Usuario user = validarUsuario(correo, clave);

            if (user == null) {
                res.status(401); 
                // Corregido el enlace de error
                return "<h1>Error: Datos incorrectos</h1><p><a href='/html/login.html'>Volver a intentar</a></p>";
            }

            if (user.getRol().equalsIgnoreCase("admin")) {
                res.redirect("/web1?usuario=" + user.getNombre());
            } else {
                // Corregido: Redirige a la carpeta html
                res.redirect("/html/index.html");
            }
            return null;
        });

        // PROCESO DE REGISTRO
        post("/registro", (req, res) -> {
            String nombre = req.queryParams("nombre");
            String email = req.queryParams("email");
            String pass = req.queryParams("password");

            boolean exito = registrarUsuario(nombre, email, pass);

            if (exito) {
                // Corregido: Redirige a la carpeta html
                res.redirect("/html/login.html");
            } else {
                return "<h1>Error al registrar</h1><p>El email ya existe o hubo un fallo.</p><a href='/html/registro.html'>Volver</a>";
            }
            return null;
        });

        // PROCESO DE INSCRIPCIÓN A CURSOS
        post("/inscribir", (req, res) -> {
            String email = req.queryParams("email");
            int idCurso = Integer.parseInt(req.queryParams("id_curso"));

            boolean exito = inscribirAlumno(email, idCurso);

            if (exito) {
                return """
                    <!doctype html>
                    <html>
                    <body style='font-family: sans-serif; text-align: center; padding: 50px;'>
                        <h1 style='color: green;'>¡Inscripción Confirmada!</h1>
                        <p>Tus datos se han guardado en AWS.</p>
                        <a href='/html/index.html' style='padding: 10px; background: #333; color: white; text-decoration: none;'>Volver al Inicio</a>
                    </body>
                    </html>
                    """;
            } else {
                // Corregido el enlace
                return "<h1>Error en la inscripción</h1><p>Verifica que el email sea correcto y estés registrado.</p><a href='/html/cursoswp.html'>Volver</a>";
            }
        });

        // PANEL DE ADMINISTRADOR
        get("/web1", (req, res) -> {
            String nombre = req.queryParams("usuario");
            res.type("text/html");
            return """
                <!doctype html>
                <html lang="es">
                <head><meta charset="utf-8"><title>Admin | LORTU</title></head>
                <body style='font-family: Arial; padding: 40px; text-align: center; background-color: #f4f4f4;'>
                    <div style='background: white; padding: 30px; border-radius: 10px; box-shadow: 0 0 10px rgba(0,0,0,0.1);'>
                        <h1 style='color: #2563eb;'>Panel de Gestión LORTU</h1>
                        <h2>Bienvenido, %s</h2>
                        <p>Estado: <strong>Conectado a AWS</strong></p>
                        <br>
                        <a href='/html/index.html'>Ir a la web principal</a>
                    </div>
                </body>
                </html>
                """.formatted(nombre);
        });
    }

    // --- 3. MÉTODOS DE BASE DE DATOS ---
    // (Estos no cambian, las consultas SQL son independientes de las carpetas HTML)

    private static Usuario validarUsuario(String email, String pass) {
        String sql = "SELECT nombre, email, rol FROM usuarios WHERE email = ? AND password = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, pass);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Usuario(rs.getString("nombre"), rs.getString("email"), rs.getString("rol"));
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
        String sql = "INSERT INTO inscripciones (id_usuario, id_curso) VALUES " +
                     "((SELECT id_usuario FROM usuarios WHERE email = ?), ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, emailAlumno);
            ps.setInt(2, idCurso);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Error SQL en inscripción: " + e.getMessage());
            return false;
        }
    }
}