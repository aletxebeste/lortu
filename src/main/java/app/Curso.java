package app;

public class Curso {
    private int idCurso;
    private String nombre;
    private int horas;
    private int plazasMax;
    private boolean subvencionado;
    private int idAula;

    public Curso(int idCurso, String nombre, int horas, int plazasMax, boolean subvencionado, int idAula) {
        this.idCurso = idCurso;
        this.nombre = nombre;
        this.horas = horas;
        this.plazasMax = plazasMax;
        this.subvencionado = subvencionado;
        this.idAula = idAula;
    }

    public int getIdCurso() { return idCurso; }
    public String getNombre() { return nombre; }
    public int getHoras() { return horas; }
    public int getPlazasMax() { return plazasMax; }
    public boolean isSubvencionado() { return subvencionado; }
    public int getIdAula() { return idAula; }
}