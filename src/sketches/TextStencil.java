package sketches;

import geomerative.RContour;
import geomerative.RGeomElem;
import geomerative.RGroup;
import geomerative.RMatrix;
import geomerative.RPolygon;
import geomerative.RFont;

import processing.core.PApplet;

public class TextStencil extends PApplet {
	private static final long serialVersionUID = 1L;

	public RFont arialBlack;
	public RFont libSansBold;
	public RFont freeScript;
	
	public RPolygon wordDiff;
	public RPolygon wordBoundsPoly;
	
	public RPolygon charDiff;
	public RPolygon charBoundsPoly;
	
	int defaultFill;
	int baseFontSize;
	
	float rotAngle;
	
	public RPolygon getStencil(String text, RFont font) {
		RGroup textShapes = font.toGroup(text);
		RContour textBounds = textShapes.getBounds();
		
		RMatrix boundsScaleMtx = new RMatrix();
		boundsScaleMtx.scale(1.25f, textBounds.getCenter());
		textBounds.transform(boundsScaleMtx);
		
		RPolygon textDiff = textBounds.toPolygon();
		for (RGeomElem chr : textShapes.elements) {
			textDiff = textDiff.diff(chr.toPolygon());
		}
		
		return textDiff;
	}
	
	public void setup() {
		size(800, 800, OPENGL);
		
		baseFontSize = 144;

		libSansBold = new RFont(this, "LiberationSans-Bold.ttf", baseFontSize);
		libSansBold.setAlign(RFont.CENTER);
		
		arialBlack = new RFont(this, "ariblk.TTF", baseFontSize);
		arialBlack.setAlign(RFont.CENTER);
		
		freeScript = new RFont(this, "FREESCPT.TTF", baseFontSize);
		freeScript.setAlign(RFont.CENTER);
		
		wordDiff = getStencil("BLUE JELLO", arialBlack);
		wordBoundsPoly = wordDiff.getBounds().toPolygon();
		
		charDiff = getStencil("green jello", freeScript);
		charBoundsPoly = charDiff.getBounds().toPolygon();

		defaultFill = 0;
		rotAngle = 0;
		
		stroke(0);
		strokeWeight(2);
		fill(defaultFill);
		
		noStroke();
		smooth();
	}
	
	public void draw() {
		background(255);
		
		pushMatrix();
		{
			translate(mouseX, mouseY);
			scale((float)mouseY / height);
			rotateY(rotAngle);
			
			fill(0, 255, 0);
			wordBoundsPoly.draw(this);
			fill(defaultFill);
			
			pushMatrix();
			{
				translate(0, 0, 0.05f);
				wordDiff.draw(this);
			}
			popMatrix();
			
//			stroke(0);
//			line(-800, 0, 800, 0);
//			line(-800, -baseFontSize, 800, -baseFontSize);
//			noStroke();
		}
		popMatrix();
		
		pushMatrix();
		{
			translate(mouseX, mouseY);
			scale((float)mouseY / height);
			translate(0, (int)(baseFontSize * 1.25));
			rotateY(rotAngle);
			
			fill(255, 0, 0);
			charBoundsPoly.draw(this);
			fill(defaultFill);
			
			pushMatrix();
			{
				translate(0, 0, 0.05f);
				charDiff.draw(this);
			}
			popMatrix();
			
//			stroke(0);
//			line(-800, 0, 800, 0);
//			line(-800, -baseFontSize, 800, -baseFontSize);
//			noStroke();
		}
		popMatrix();
		
		rotAngle += PI / 120f;
	}
}
