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
package model;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import utils.Matrix;

/**
 * An independent trianglular finite element, which has three vertices that
 * may be shared with other Elements, as well as some material properties.
 * 
 * @author Justin Kunimune
 */
public class Element {
	
	private final double strength; // the max stress before tearing
	private final double lambda, mu; // the elastic properties
	
	private final Vertex[] vertices; //which vertex is attached to each sector (there must be exactly one)
	private final double[][] undeformedCoords; // the undeformed x-y coordinates of each vertex (each Vertex has a different undeformed coordinate in each Element to which it's connected
	private final double area; // undeformed area
	
	private double defaultEnergy; // the stored elastic potential energy
	
	
	
	public Element(double strength, double lambda, double mu,
			Vertex[] vertices, double[][] coords) {
		this.strength = strength;
		this.lambda = lambda;
		this.mu = mu;
		
		this.vertices = vertices;
		this.undeformedCoords = coords;
		this.area = (coords[0][0]*coords[1][1] + coords[1][0]*coords[2][1] + coords[2][0]*coords[0][1] -
				(coords[1][0]*coords[0][1] + coords[2][0]*coords[1][1] + coords[0][0]*coords[2][1])) / 2.;
		assert area >= 0;
		
		assert vertices.length == 3 : vertices.length;
		for (Vertex v: vertices) // make sure this reference goes both ways
			v.addNeighbor(this);
	}
	
	
	/**
	 * Compute the strain energy density times volume for this cell, save it and return it.
	 * @return the energy felt by this cell
	 */
	double computeAndSaveEnergy() {
		this.defaultEnergy = getCurrentEnergy();
		return this.defaultEnergy;
	}
	
	/**
	 * Compute the difference in energy in this cell between this state and the default state.
	 * @return the increase in energy
	 */
	double computeDeltaEnergy() {
		return getCurrentEnergy() - this.defaultEnergy;
	}
	
	/**
	 * Compute the total energy in the cell in this current configuration.
	 * @return the current energy.
	 */
	private double getCurrentEnergy() {
		if (vertices[0] == vertices[1] || vertices[1] == vertices[2] || vertices[2] == vertices[0])
			return 0; // the energy of a degenerate triangle will always be zero
//		System.out.println(Arrays.deepToString(this.undeformedCoords));
//		for (int i = 0; i < 3; i ++)
//			System.out.println("("+vertices[i].getLat()+", "+vertices[i].getLon()+"); <"+vertices[i].getX()+", "+vertices[i].getY()+">");
		Matrix F = new Matrix(2, 2); // this is the deformation gradient
		for (int i = 0; i < 3; i ++) { // it has a lot of terms
			double XA = vertices[i].getX(), YA = vertices[i].getY();
			double xB = undeformedCoords[(i+1)%3][0], yB = undeformedCoords[(i+1)%3][1];
			double xC = undeformedCoords[(i+2)%3][0], yC = undeformedCoords[(i+2)%3][1];
			F = F.plus(new Matrix(2, 2, // so I populate it in this for-loop
					XA*(xB - xC), XA*(yB - yC),
					YA*(xB - xC), YA*(yB - yC)).over(2*area));
		}
		Matrix B = F.times(F.T()); // the rest is fancy Neo-Hookean stuff
		double J = F.det();
		double i1 = B.tr();
//		assert Double.isFinite((mu/2*(i1 - 2 - 2*Math.log(J)) + lambda/2*Math.pow(Math.log(J), 2)) * area) : area+", "+J+", "+i1+", "+F;
		return (mu/2*(i1 - 2 - 2*Math.log(J)) + lambda/2*Math.pow(Math.log(J), 2)) * area; // don't forget to multiply energy density by undeformed volume
		
	}
	
	/**
	 * Does this element contain these undeformed coordinates?
	 * @param x - the undeformed x to test
	 * @param y - the undeformed y to test
	 * @return true if these undeformed coordinates fall inside the undeformed element, false otherwise
	 */
	public boolean containsUndeformed(double x, double y) {
		return containsUndeformed(x, y, -1);
	}
	
	/**
	 * Does this element contain these undeformed coordinates if we open up one side and
	 * make it an intersection of two hemiplanes?
	 * @param x - the undeformed x to test
	 * @param y - the undeformed y to test
	 * @param openSide - the index of the Vertex across from the open side
	 * @return true if these undeformed coordinates fall inside the open undeformed element, false otherwise
	 */
	public boolean containsUndeformed(double x, double y, int openSide) {
		double xa = undeformedCoords[0][0], ya = undeformedCoords[0][1];
		double xb = undeformedCoords[1][0], yb = undeformedCoords[1][1];
		double xc = undeformedCoords[2][0], yc = undeformedCoords[2][1];
		double[] w = new double[3];
		double denom = (yb-yc)*(xa-xc) - (xb-xc)*(ya-yc);
		w[0] = ((yb-yc)*(x-xc) - (xb-xc)*(y-yc)) / denom; // simple barycentric coordinates
		w[1] = ((yc-ya)*(x-xc) - (xc-xa)*(y-yc)) / denom;
		w[2] = 1 - w[0] - w[1];
		
		for (int i = 0; i < 3; i ++) // if any of the barycentric coords are less than 0
			if (i != openSide && w[i] < 0)
				return false;
		return true;
	}
	
	/** Linearly interpolate a pair of X-Y coordinates from the given spherical coordinates.
	 * @param x - the input x
	 * @param y - the input y
	 * @return {output X, output Y}
	 */
	public double[] mapUndeformedToDeformed(double x, double y) {
		double xa = undeformedCoords[0][0], ya = undeformedCoords[0][1], Xa = vertices[0].getX(), Ya = vertices[0].getY();
		double xb = undeformedCoords[1][0], yb = undeformedCoords[1][1], Xb = vertices[1].getX(), Yb = vertices[1].getY();
		double xc = undeformedCoords[2][0], yc = undeformedCoords[2][1], Xc = vertices[2].getX(), Yc = vertices[2].getY();
		
		double denom = (yb-yc)*(xa-xc) - (xb-xc)*(ya-yc); // simple barycentric coordinates
		double wa = ((yb-yc)*(x-xc) - (xb-xc)*(y-yc)) / denom;
		double wb = ((yc-ya)*(x-xc) - (xc-xa)*(y-yc)) / denom;
		double wc = 1 - wa - wb;
		
		return new double[] {wa*Xa + wb*Xb + wc*Xc, wa*Ya + wb*Yb + wc*Yc};
	}
	
	public boolean isDegenerate() {
		return vertices[0] == vertices[1] || vertices[1] == vertices[2] || vertices[2] == vertices[0];
	}
	
	public double getStrength() {
		return this.strength;
	}
	
	public double getCX() {
		double x = 0;
		for (Vertex v: vertices)
			x += v.getX();
		return x/4;
	}
	
	public double getCY() {
		double y = 0;
		for (Vertex v: vertices)
			y += v.getY();
		return y/4;
	}
	
	
	public Vertex getVertex(int i) {
		return this.vertices[i];
	}
	
	public void setVertex(int i, Vertex corner) {
		this.vertices[i] = corner;
	}
	
	public List<Vertex> getVerticesUnmodifiable() {
		return Collections.unmodifiableList(Arrays.asList(this.vertices));
	}
	
	
	public boolean isAdjacentTo(Element that) { // kitty-corner cells don't count
		Set<Vertex> sharedVertices = new HashSet<Vertex>();
		for (Vertex v: this.getVerticesUnmodifiable())
			if (that.getVerticesUnmodifiable().contains(v))
				sharedVertices.add(v); // it's kind of annoying that I need a Hash and can't use an int,
		return sharedVertices.size() >= 2; // but it doesn't count as two shared corners if they're the same Vertex
	}
	
	public boolean isAdjacentTo(Vertex v) {
		return this.getVerticesUnmodifiable().contains(v);
	}
	
	
	@Override
	public String toString() {
		String s = "Element(";
		for (Vertex v: this.vertices)
			s += v+", ";
		return s.substring(0,s.length()-2) + ")";
	}
	
	
	public static final void main(String[] args) {
		Vertex[] vertices = {new Vertex(-3,-3, 0, 0), new Vertex(1,0, 1, 0), new Vertex(0,-1, 0, 2)};
		double[][] coords = {               {0,0},                {1, 0},                {0, 1}};
		Element e = new Element(1, 1, 1, vertices, coords);
		
		System.out.println(e.area);
		
		System.out.println(e.containsUndeformed(.1, .1));
		System.out.println(e.containsUndeformed(0, 0));
		System.out.println(e.containsUndeformed(0, 1));
		System.out.println(e.containsUndeformed(1, 0));
		System.out.println(e.containsUndeformed(.5,.5));
		System.out.println(e.containsUndeformed(-1, 0));
		System.out.println(e.containsUndeformed(0, 2));
		System.out.println(e.containsUndeformed(1, 1));
		System.out.println(e.containsUndeformed(1, .1));

		System.out.println(Arrays.toString(e.mapUndeformedToDeformed(1, 0)));
		System.out.println(Arrays.toString(e.mapUndeformedToDeformed(.5, .5)));
		System.out.println(Arrays.toString(e.mapUndeformedToDeformed(0, 1)));
		
		vertices[2].setVel(0, -1);
		for (double y = 2; y > -.6; y -= .25) {
			System.out.println(vertices[2].getY()+": "+e.getCurrentEnergy());
			vertices[2].descend(.25);
		}
	}
}
