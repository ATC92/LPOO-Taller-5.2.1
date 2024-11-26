import java.util.concurrent.*;

class Inventario {
    private final ConcurrentHashMap<String, Integer> stock = new ConcurrentHashMap<>();

    public Inventario() {
        stock.put("Pizza", 10);
        stock.put("Hamburguesa", 15);
    }

    public synchronized boolean actualizarStock(String producto, int cantidad) {
        if (stock.getOrDefault(producto, 0) >= cantidad) {
            stock.put(producto, stock.get(producto) - cantidad);
            return true;
        }
        return false;
    }

    public synchronized int consultarStock(String producto) {
        return stock.getOrDefault(producto, 0);
    }
}

class Pedido {
    private final String cliente;
    private final String producto;

    public Pedido(String cliente, String producto) {
        this.cliente = cliente;
        this.producto = producto;
    }

    public String getCliente() {
        return cliente;
    }

    public String getProducto() {
        return producto;
    }
}

class SistemaDePedidos {
    private final BlockingQueue<Pedido> colaPedidos = new LinkedBlockingQueue<>();
    private final Inventario inventario;

    public SistemaDePedidos(Inventario inventario) {
        this.inventario = inventario;
    }

    public void realizarPedido(String cliente, String producto) {
        if (inventario.actualizarStock(producto, 1)) {
            colaPedidos.add(new Pedido(cliente, producto));
            System.out.println("Pedido realizado: " + cliente + " pidió " + producto);
        } else {
            System.out.println("Stock insuficiente para " + producto);
        }
    }

    public Pedido procesarPedido() throws InterruptedException {
        return colaPedidos.take(); // Bloquea si no hay pedidos.
    }

    public boolean colaPedidosVacia() {
        return colaPedidos.isEmpty();
    }
}

class Cliente implements Runnable {
    private final SistemaDePedidos sistema;
    private final String nombre;
    private final String producto;

    public Cliente(SistemaDePedidos sistema, String nombre, String producto) {
        this.sistema = sistema;
        this.nombre = nombre;
        this.producto = producto;
    }

    @Override
    public void run() {
        sistema.realizarPedido(nombre, producto);
    }
}

class Repartidor implements Runnable {
    private final SistemaDePedidos sistema;
    private volatile boolean running = true; // Indicador para detener el hilo.

    public Repartidor(SistemaDePedidos sistema) {
        this.sistema = sistema;
    }

    public void stop() {
        running = false; // Cambia el indicador para detener el hilo.
    }

    @Override
    public void run() {
        try {
            while (running) { // Continua solo si el indicador esta en true.
                Pedido pedido = sistema.procesarPedido();
                System.out.println("Repartidor entregó el pedido de: " + pedido.getCliente());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Limpieza si el hilo fue interrumpido.
            System.out.println("Repartidor interrumpido.");
        }
    }
}

public class Main {
    public static void main(String[] args) {
        Inventario inventario = new Inventario();
        SistemaDePedidos sistema = new SistemaDePedidos(inventario);

        Thread cliente1 = new Thread(new Cliente(sistema, "Alice", "Pizza"));
        Thread cliente2 = new Thread(new Cliente(sistema, "Bob", "Hamburguesa"));
        Repartidor repartidorRunnable = new Repartidor(sistema);
        Thread repartidor = new Thread(repartidorRunnable);
        
        // Inicia los hilos.
        cliente1.start();
        cliente2.start();
        // Esperar a que los clientes terminen.
        try {
            cliente1.join();
            cliente2.join();
        } catch (InterruptedException e) {
            System.out.println("El hilo principal fue interrumpido mientras esperaba a los clientes.");
            Thread.currentThread().interrupt(); // Reestablecer el estado de interrupcion.
        }
        
        repartidor.start(); 
        System.out.println("");
        // Esperar a que todos los pedidos sean procesados.
        while (!sistema.colaPedidosVacia()) {
            try {
                Thread.sleep(100); // Espera breve para evitar un ciclo ocupado.
            } catch (InterruptedException e) {
                System.out.println("El hilo principal fue interrumpido mientras esperaba que se vaciara la cola de pedidos.");
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("");
        // Detener al repartidor despues de cierto tiempo o condicion.
        System.out.println("Deteniendo repartidor...");
        repartidorRunnable.stop(); // Cambiar el indicador.
        repartidor.interrupt();    // Despertar al hilo si esta bloqueado en `take()`.

        // Esperar a que el repartidor termine.
        try {
            repartidor.join();
        } catch (InterruptedException e) {
            System.out.println("El hilo principal fue interrumpido mientras esperaba al repartidor.");
            Thread.currentThread().interrupt();
        }
        System.out.println("Sistema terminado.");
    }
}
