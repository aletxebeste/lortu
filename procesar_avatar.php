<?php
$nombre = $_POST['nombre'] ?? 'Usuario';

// Carpeta donde se guardarán los avatares
$carpeta = "uploads/";

// Crear carpeta si no existe
if (!is_dir($carpeta)) {
    mkdir($carpeta, 0777, true);
}

// Nombre único para la imagen
$nombreArchivo = time() . "_" . basename($_FILES["avatar"]["name"]);
$rutaDestino = $carpeta . $nombreArchivo;

// Mover archivo subido
if (move_uploaded_file($_FILES["avatar"]["tmp_name"], $rutaDestino)) {
    // Redirigir al dashboard con avatar y nombre
    header("Location: dashboard_usuario.php?nombre=$nombre&avatar=$rutaDestino");
    exit();
} else {
    echo "Error al subir el archivo.";
}
?>