public class SimpleApp {
	public static void main(String[] args) {
		new Thread(new Runnable1()).start();
	}

	static class Runnable1 implements Runnable {
		@Override
		public void run() {
			while (true) {
				int size = 1024 * 8 * 1000;
				Object[] objects = new Object[size];
				for (int i = 0; i < size; i++) {
					objects[i] = new Object();
				}
				Runtime rt = Runtime.getRuntime();
				System.out.println("free memory: " + rt.freeMemory());
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				System.out.println(objects);

			}

		}

	}
}
