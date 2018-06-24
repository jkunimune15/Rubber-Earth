/**
 * MIT License
 * 
 * Copyright (c) 2018 Justin Kunimune
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package model;

import linalg.Vector;

/**
 * A single point in the rubber mesh.
 * 
 * @author Justin Kunimune
 */
public class Vertex {
	
	public static final int EAST = 0, NORTH = 1, WEST = 2, SOUTH = 3;
	
	private final Vertex[] neighbors = new Vertex[4]; //the four neighbors (might be null)
//	private final VertexSet sisters; //any vertices that occupy the same spot on the globe
	private final double delP, delL; //the latitudinal and longitudinal spans
	private double x, y; //the current planar coordinates
	private Vector netForce;
	
	
	public Vertex(double delP, double delL, double x, double y) {
		this.delP = delP;
		this.delL = delL;
		this.x = x;
		this.y = y;
//		this.sisters = new VertexSet(this);
	}
	
	
	public Vertex(Vertex sister) {
		this.delP = sister.delP;
		this.delL = sister.delL;
		this.x = sister.x;
		this.y = sister.y;
//		this.sisters = sister.sisters;
//		this.sisters.add(this);
	}
	
	
	/**
	 * Compute the force on this vertex, the stress divergence, and save it.
	 */
	public void computeForce() {
		this.netForce = new Vector(2);
		netForce.set(0, Math.random()*2-1);
		netForce.set(1, Math.random()*2-1);
	}
	
	
	public void descend(double timestep) {
		this.x += timestep*netForce.get(0);
		this.y += timestep*netForce.get(1);
	}
	
	
	public void setNeighbor(int direction, Vertex neighbor) {
		neighbors[direction] = neighbor;
	}
	
	
	public double getX() {
		return x;
	}
	
	
	public double getY() {
		return y;
	}
	
	
	public Vertex getNeighbor(int direction) {
		return neighbors[direction];
	}
}
