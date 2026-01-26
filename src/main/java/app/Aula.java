package app;

public class Aula {
    private int idAula;
    private String nombre;
    private int capacidad;

    public Aula(int idAula, String nombre, int capacidad) {
        this.idAula = idAula;
        this.nombre = nombre;
        this.capacidad = capacidad;
    }

    public int getIdAula() { return idAula; }
    public String getNombre() { return nombre; }
    public int getCapacity() { return capacidad; }
}