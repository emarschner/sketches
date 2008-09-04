package sketches;

import geomerative.RCommand;
import geomerative.RContour;
import geomerative.RFont;
import geomerative.RGroup;
import geomerative.RMatrix;
import geomerative.RPoint;
import geomerative.RPolygon;

import processing.core.PApplet;

public class TextMask extends PApplet {
	private static final long serialVersionUID = 1L;
	
	int maskFontSize = 288;
	int baseFontSize = 36;
	int osdFontSize = 18;

	RFont maskFont;
	RFont baseFont;
	RFont osdFont;
	
	RPolygon maskCharPoly;
	float maskCharHeight;
	float maskCharWidth;
	RGroup maskDimsText;
	RPoint maskCenter;
	
	RPolygon baseCharPoly;
	float baseCharHeight;
	float baseCharWidth;
	RGroup baseCharGroup;
	RPolygon baseCharGroupPoly;
	RGroup baseDimsText;
	RPoint baseCenter;
	
	RPolygon charDiff;
	RPoint charDiffCenter;
	
	float rotAngle;

	public void setup() {
		size(400, 400, OPENGL);
		
		RCommand.setSegmentator(RCommand.UNIFORMSTEP);
		RCommand.setSegmentStep(-1);
		
		background(255);
		noStroke();
		fill(128, 128, 128, 128);
		smooth();

		maskFont = new RFont(this, "ariblk.TTF", maskFontSize);
		maskFont.setAlign(RFont.CENTER);
		baseFont = new RFont(this, "ariblk.TTF", baseFontSize);
		baseFont.setAlign(RFont.CENTER);
		osdFont = new RFont(this, "ariblk.TTF", osdFontSize);
		
		RContour bounds;
		
		maskCharPoly = maskFont.toPolygon('h');
		bounds = maskCharPoly.getBounds();
		maskCharHeight = getContourHeight(bounds);
		maskCharWidth = getContourWidth(bounds);
		maskDimsText = osdFont.toGroup(maskCharWidth + "x" + maskCharHeight);
		maskCenter = maskCharPoly.getCenter();
		
		baseCharPoly = baseFont.toPolygon('x');
		bounds = baseCharPoly.getBounds();
		baseCharHeight = getContourHeight(bounds);
		baseCharWidth = getContourWidth(bounds);
		baseDimsText = osdFont.toGroup(baseCharWidth + "x" + baseCharHeight);
		
		int numCols = ceil(maskCharWidth / baseCharWidth);
		int numRows = ceil(maskCharHeight / baseCharHeight);
		
		baseCharGroup = new RGroup();
		RMatrix posMtx;
		RPolygon tmpPoly;
		for (int i = 0; i < numCols; ++i) {
			for (int j = 0; j < numRows; ++j) {
				posMtx = new RMatrix();
				posMtx.translate((float)i * (baseCharWidth + 2f), -(float)j * (baseCharHeight + 2f));
				
				tmpPoly = new RPolygon(baseCharPoly);
				tmpPoly.transform(posMtx);
				
				baseCharGroup.addElement(tmpPoly);
			}
		}
		baseCharGroupPoly = baseCharGroup.toPolygon();
		baseCharGroupPoly.update();
		baseCenter = baseCharGroupPoly.getCenter();
		
		posMtx = new RMatrix();
		posMtx.translate(maskCenter.x - baseCenter.x, maskCenter.y - baseCenter.y);
		baseCharGroupPoly.transform(posMtx);
		
		charDiff = maskCharPoly.intersection(baseCharGroupPoly);
		charDiff.update();
		charDiffCenter = charDiff.getCenter();
		
		posMtx = new RMatrix();
		posMtx.translate(-charDiffCenter.x, -charDiffCenter.y);
		charDiff.transform(posMtx);
		
		charDiffCenter = charDiff.getCenter();
	}
	
	public void draw() {
		background(255);
		
		pushMatrix();
		{
//			translate(width / 2 - charDiffCenter.x, height / 2 - charDiffCenter.y);
			translate(mouseX, mouseY);
			rotateY(rotAngle);
			
//			maskCharPoly.draw(this);
//			baseCharGroupPoly.draw(this);
			
			charDiff.draw(this);
		}
		popMatrix();
		
		rotAngle += PI / 120f;
		rotAngle %= 2 * PI;
	}
	
	float getContourHeight(RContour contour) {
		float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
		
		for (RPoint point : contour.points) {
			minY = (point.y < minY) ? point.y : minY;
			maxY = (point.y > maxY) ? point.y : maxY;
		}
		
		return maxY - minY;
	}
	
	float getContourWidth(RContour contour) {
		float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
		
		for (RPoint point : contour.points) {
			minX = (point.x < minX) ? point.x : minX;
			maxX = (point.x > maxX) ? point.x : maxX;
		}
		
		return maxX - minX;
	}
}
