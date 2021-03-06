/**
 * This is free and unencumbered software released into the public domain.
 * 
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 * 
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 * 
 * For more information, please refer to <http://unlicense.org>
 */
package view;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOError;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.imaging.ImageReadException;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.stage.Stage;
import javafx.util.Duration;
import model.Mesh;
import utils.ImgUtils;


/**
 * The main class for running and stuff.
 * 
 * @author Justin Kunimune
 */
public final class Main extends Application {
	
	public static final String CONFIG_FILENAME = "optimal";
	public static final int MESH_RESOLUTION = 40; // the number of nodes from the equator to the pole
	public static final double PRECISION = 1e-6; // if the energy changes by less than this in one step, we're done
	public static final double ECCENTRICITY = 0.081819;
	public static final int VIEW_SIZE = 800; // size of the viewing window
	public static final int MARGIN_SIZE = 160;
	public static final double MAX_FRAME_RATE = .2; // don't render more frames than this per second
	public static final double DECAY_TIME = 500; // the number of seconds that it smoothes
	public static final boolean DRAW_MESH = false;
	public static final boolean SAVE_IMAGES = true; // save renderings as images for later processing
	public static final String[] GEO_DATA_SOURCES = {
			"ne_110m_admin_0_countries", "ne_110m_graticules_15"};
//			"ne_110m_ocean", "ne_110m_graticules_15"};
	
	private final String numeral;
	private final String description;
	private final Mesh mesh;
	private final Renderer renderer;
	private Task<Void> modelWorker;
	private ScheduledService<Void> viewWorker;
	
	
	public Main() throws IOException {
		Properties config = new Properties();
		config.load(new FileReader(String.format("config/%s.properties", CONFIG_FILENAME)));
		
		this.numeral = config.getProperty("numeral");
		description = config.getProperty("desc");
		System.out.printf("Loaded parameters for projection %s: %s\n", numeral, description);
		String INITIAL_CONDITION = 					config.getProperty("init", "hammer");
		double LAMBDA = Double.parseDouble(			config.getProperty("lambda", "1.0"));
		double MU = Double.parseDouble(				config.getProperty("mu", "1.0"));
		double TEAR_LENGTH = Double.parseDouble(	config.getProperty("tear", "0.0"));
		String WEIGHTS_FILENAME = 					config.getProperty("weightsFilename", "null");
		double WEIGHTS_LOGBASE = Double.parseDouble(config.getProperty("weightsLogbase", "0.0"));
		double WEIGHTS_MINVAL = Double.parseDouble(	config.getProperty("weightsMinval", "0.0"));
		String SCALES_FILENAME = 					config.getProperty("scalesFilename", "null");
		double SCALES_LOGBASE = Double.parseDouble(	config.getProperty("scalesLogbase", "0.0"));
		double SCALES_MINVAL = Double.parseDouble(	config.getProperty("scalesMinval", "0.0"));
		
		double[][] WEIGHT_ARRAY = null, SCALE_ARRAY = null;
		try {
			if (!WEIGHTS_FILENAME.equals("null"))
				WEIGHT_ARRAY = ImgUtils.loadTiffData( // load the Tiff files if necessary
						WEIGHTS_FILENAME, MESH_RESOLUTION, WEIGHTS_LOGBASE, 1, WEIGHTS_MINVAL);
		} catch (ImageReadException e) {
			System.err.println("Warning: unreadable Tiff file.");
		}
		if (WEIGHT_ARRAY == null)
			WEIGHT_ARRAY = ImgUtils.uniform(MESH_RESOLUTION); // default to uniform weight
		
		try {
			if (!SCALES_FILENAME.equals("null"))
				SCALE_ARRAY = ImgUtils.standardised(ImgUtils.loadTiffData(
						SCALES_FILENAME, MESH_RESOLUTION, SCALES_LOGBASE, 1, SCALES_MINVAL));
		} catch (ImageReadException e) {
			System.err.println("Warning: unreadable Tiff file.");
		}
		if (SCALE_ARRAY == null)
			SCALE_ARRAY = ImgUtils.uniform(MESH_RESOLUTION); // default to uniform scale
		
		mesh = new Mesh( // create the mesh and renderer
				MESH_RESOLUTION, INITIAL_CONDITION, LAMBDA, MU, PRECISION, TEAR_LENGTH,
				WEIGHT_ARRAY, SCALE_ARRAY, ECCENTRICITY);
		renderer = new Renderer(
				VIEW_SIZE, MARGIN_SIZE, mesh, DECAY_TIME,
				INITIAL_CONDITION.startsWith("az") ? 2*Math.PI : 4*Math.sqrt(2), DRAW_MESH, SAVE_IMAGES, GEO_DATA_SOURCES,
				LAMBDA, MU, TEAR_LENGTH);
	}
	
	
	@Override
	public void start(Stage root) throws Exception {
		root.setTitle("Creating the perfect map̤…");
		root.setScene(renderer.getScene());
		
		modelWorker = new Task<Void>() {
			private long start, end;
			
			protected Void call() throws Exception {
				System.out.println("Starting mesh optimisation...");
				start = System.currentTimeMillis();
				while (!isCancelled()){
					if (!mesh.update()) // make as good a map as you can
						if (!mesh.rupture()) // or tear if you're done updating
							if (!mesh.stitch()) // or start untearing if you're done tearing
								break; // or quit if you're done with that, too
				}
				mesh.finalise(); // and now it's done
				return null;
			}
			
			protected void succeeded() {
				super.succeeded();
				end = System.currentTimeMillis(); // note the time of success
				root.setTitle(String.format("Introducing the Danseiji %s projection!", numeral));
				
				PrintStream log = null; // open a log
				try {
					log = new PrintStream(new File(String.format("output/danseiji%s%d.log", numeral, MESH_RESOLUTION)));
				} catch (IOException e) {
					System.err.println("Could not open log file: ");
					e.printStackTrace();
					log = System.out; // or at least try
				}
				
				log.println(String.format("It finished in %.1f min.", (end-start)/60000.)); // report results
				log.println(String.format("The final convergence is %.3fJ.", mesh.getTotEnergy()));
				
				try {
					double[] criteria; // report Kavrayskiy's distortion criteria
					criteria = mesh.getCriteria(
							ImgUtils.uniform(MESH_RESOLUTION));
					log.println(String.format("The global      areal distortion is %+.3f ± %.3f Np, and the "
							+ "global      angular distortion is %.3f Np.", criteria[0], criteria[1], criteria[2]));
					criteria = mesh.getCriteria(
							ImgUtils.loadTiffData("SRTM_RAMP2_TOPO_2000-02-11_gs_3600x1800", MESH_RESOLUTION, 0, 1, 0));
					log.println(String.format("The terrestrial areal distortion is %+.3f ± %.3f Np, and the "
							+ "terrestrial angular distortion is %.3f Np.", criteria[0], criteria[1], criteria[2]));
					criteria = mesh.getCriteria(
							ImgUtils.loadTiffData("SRTM_RAMP2_TOPO_2000-02-11_gs_3600x1800", MESH_RESOLUTION, 0, 0, 1));
					log.println(String.format("The nautical    areal distortion is %+.3f ± %.3f Np, and the "
							+ "nautical    angular distortion is %.3f Np.", criteria[0], criteria[1], criteria[2]));
				} catch (IOException | ImageReadException e) {
					System.err.println("Could not load data files for localized evaluation.");
				}
				
				if (log != System.out) // close log
					try {
						log.close();
					} catch (IOError e) {
						System.err.println("Could not close stream.");
						e.printStackTrace();
					}
				
				new Thread(() -> {
					try {
						System.out.println("Saving mesh...");
						mesh.save(new PrintStream(new File(String.format("output/danseiji%s%d.csv", numeral, MESH_RESOLUTION)))); // save the mesh!
						System.out.println("Saved mesh!"); // save it, please.
					} catch (FileNotFoundException e1) {
						e1.printStackTrace();
					}
				}).start();
				
				new Timer().schedule(new TimerTask() { // after giving it a moment to settle,
					public void run() {
						Platform.runLater(() -> {
							viewWorker.cancel(); // tell the viewer to stop updating
							try { // and to save the final map
								renderer.saveImage(String.format("output/danseiji%s.png", numeral), false);
							} catch (IOException e) {
								System.err.println("Could not save final image for some reason.");
								e.printStackTrace();
							}
							if (SAVE_IMAGES) {
								try { // and to make those frames into a movie if we have them
									ImgUtils.compileFrames("frames", "convergence "+numeral, renderer.getNumFrames());
								} catch (IOException e) {
									System.err.println("Could not compile frames for some reason.");
									e.printStackTrace();
								}
							}
						});
					}
				}, (long)(1000*4*DECAY_TIME));
			}
			
			protected void failed() {
				super.failed();
				this.getException().printStackTrace(System.err);
				viewWorker.cancel();
			}
		};
		
		viewWorker = new ScheduledService<Void>() {
			protected Task<Void> createTask() {
				return new Task<Void>() {
					protected Void call() throws Exception {
						renderer.render();
						return null;
					}
				};
			}
			
			protected void failed() {
				super.failed();
				if (this.getException() != null)
					this.getException().printStackTrace(System.err);
				else
					System.err.println("It aborted, but there's no error?");
			}
		};
		viewWorker.setPeriod(Duration.seconds(1./MAX_FRAME_RATE));;
		viewWorker.setExecutor(Platform::runLater);
		
		new Thread(modelWorker).start();
		viewWorker.start();
		root.show();
	}
	
	
	@Override
	public void stop() {
		modelWorker.cancel();
	}
	
	
	public static void main(String[] args) {
		launch(args);
	}
}
