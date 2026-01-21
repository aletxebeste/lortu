<?php
$nombre = $_GET['nombre'] ?? 'Administrador';
$avatar = $_GET['avatar'] ?? 'images/admin-avatar.png'; // Puedes poner un avatar especial
?>

<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <title>Panel Admin | LORTU</title>
  <link rel="stylesheet" href="admin.css">
  <link href="https://fonts.googleapis.com/css2?family=Questrial&display=swap" rel="stylesheet">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>

<body>

  <div class="admin-container">

    <aside class="sidebar">
      <div class="admin-info">
        <img src="<?php echo $avatar; ?>" class="admin-avatar" alt="Avatar Admin">
        <h3><?php echo htmlspecialchars($nombre); ?></h3>
        <p>Administrador</p>
      </div>

      <nav class="menu">
        <a href="#">📚 Gestionar cursos</a>
        <a href="#">👥 Gestionar usuarios</a>
        <a href="#">📊 Estadísticas</a>
        <a href="#">⚙️ Configuración</a>
        <a href="index.html" class="logout">Cerrar sesión</a>
      </nav>
    </aside>

    <main class="content">
      <h1>Panel de Administración</h1>
      <p>Bienvenido, <?php echo htmlspecialchars($nombre); ?>. Aquí puedes gestionar todo LORTU.</p>

      <div class="cards">
        <div class="card">Cursos activos: 4</div>
        <div class="card">Usuarios registrados: 248</div>
        <div class="card">Nuevos hoy: 5</div>
      </div>
    </main>

  </div>

</body>
</html>