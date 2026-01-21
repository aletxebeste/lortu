<?php
$nombre = $_POST['nombre'] ?? 'Usuario';
?>

<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <title>Subir Avatar | LORTU</title>
  <link rel="stylesheet" href="perfil.css">
</head>

<body>

  <div class="perfil-container">
    <div class="perfil-card">

      <h2>Hola, <?php echo htmlspecialchars($nombre); ?></h2>
      <p>Sube tu avatar para continuar</p>

      <form action="procesar_avatar.php" method="POST" enctype="multipart/form-data">
        <input type="hidden" name="nombre" value="<?php echo htmlspecialchars($nombre); ?>">

        <label>Selecciona tu avatar:</label>
        <input type="file" name="avatar" accept="image/*" required>

        <button type="submit" class="btn-primary">Guardar avatar</button>
      </form>

    </div>
  </div>

</body>
</html>