<?php

$nombre = $_POST['nombre'] ?? '';
$email = $_POST['email'] ?? '';
$password = $_POST['password'] ?? '';


if ($nombre === 'admin' && $password === '9999') {
    // El admin no sube avatar, va directo a su dashboard
    header("Location: dashboard_admin.php?nombre=admin");
    exit();
}

header("Location: perfil_subir.php?nombre=$nombre");
exit();
?>