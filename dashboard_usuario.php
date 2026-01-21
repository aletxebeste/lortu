<?php
$nombre = $_GET['nombre'] ?? 'Usuario';
$avatar = $_GET['avatar'] ?? 'images/default-avatar.png';
?>

<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <title>Tu Perfil | LORTU</title>
  <link rel="stylesheet" href="perfil.css">
</head>

<body>

  <div class="perfil-container">
    <div class="perfil-card">

      <div class="avatar-wrapper">
        <img src="<?php echo $avatar; ?>" alt="Avatar del usuario">
      </div>

      <h2><?php echo htmlspecialchars($nombre); ?></h2>
      <p class="info">Este es tu panel personal</p>

    </div>
  </div>

</body>
</html>