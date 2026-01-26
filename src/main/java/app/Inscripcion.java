package app;

public class Inscripcion {
    private int idInscripcion;
    private int idUsuario;
    private int idCurso;
    private String fechaReserva;
    private String estado;
    private double notaFinal;

    public Inscripcion(int idInscripcion, int idUsuario, int idCurso, String fechaReserva, String estado, double notaFinal) {
        this.idInscripcion = idInscripcion;
        this.idUsuario = idUsuario;
        this.idCurso = idCurso;
        this.fechaReserva = fechaReserva;
        this.estado = estado;
        this.notaFinal = notaFinal;
    }

    public int getIdInscripcion() { return idInscripcion; }
    public int getIdUsuario() { return idUsuario; }
    public int getIdCurso() { return idCurso; }
    public String getFechaReserva() { return fechaReserva; }
    public String getEstado() { return estado; }
    public double getNotaFinal() { return notaFinal; }
}