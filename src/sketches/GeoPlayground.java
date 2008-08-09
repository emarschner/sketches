package sketches;

import processing.core.PApplet;
import java.awt.event.KeyEvent;
import geomerative.RFont;
import geomerative.RShape;
import geomerative.RPolygon;
import geomerative.RContour;
import geomerative.RSubshape;
import geomerative.RCommand;
import geomerative.RMatrix;

public class GeoPlayground extends PApplet {
	RFont font = new RFont(this, "LiberationMono-Bold.ttf", 240, RFont.CENTER);
	RFont smallFont = new RFont(this, "LiberationMono-Bold.ttf", 18, RFont.CENTER);
	
	static final long serialVersionUID = 1337L;
	char character = 'a';
	char lastCharacter = 'a';
	
	Class BASE_GEOM_TYPE = RShape.class;
	
	public void setup() {
		size(400, 400, OPENGL);
		smooth();
		background(255);
		strokeWeight(2); 
	}

	//public String outlineString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	public String outlineString = "=";
	
	public void draw() {
		background(255);
		translate(width / 2, height / 2);
		if (BASE_GEOM_TYPE == RShape.class) {
			RShape someChar = font.toShape(this.character);
			RMatrix center = someChar.getCenteringTransf(g, 50f, 1f, 1f);
			float maxLength = 0;
			for (RSubshape subShape : someChar.subshapes) {
				maxLength = max(subShape.getCurveLength(), maxLength);
			}
			String renderText = getRenderText(this.lastCharacter, floor(maxLength / 25));
			for (RSubshape subShape : someChar.subshapes) {
				subShape.transform(center);
				smallFont.toGroup(renderText.substring(0, floor(renderText.length() * (subShape.getCurveLength() / maxLength)))).adaptTo(subShape).draw(g);
			}
		} else if (BASE_GEOM_TYPE == RPolygon.class) {
			RPolygon someChar = font.toPolygon(this.character);
			RMatrix center = someChar.getCenteringTransf(g, 50f, 1f, 1f);
			float maxLength = 0;
			for (RContour subContour : someChar.contours) {
				maxLength = max(subContour.toShape().subshapes[0].getCurveLength(), maxLength);
			}
			String renderText = getRenderText(this.lastCharacter, floor(maxLength / 25));
			for (RContour subContour : someChar.contours) {
				subContour.transform(center);
				smallFont.toGroup(renderText.substring(0, floor(renderText.length() * (subContour.toShape().subshapes[0].getCurveLength() / maxLength)))).adaptTo(subContour.toShape()).draw(g);
			}
		}
	}
	
	public String getRenderText(String text, int numChars) {
		text = join(split(text, " "), "");
		if (text.length() > numChars) return text.substring(0, numChars);
		String renderText = "";
		for (int i = 0; i < numChars; ++i) {
			renderText += text.charAt(i % text.length());
		}
		return renderText;
	}
	
	public String getRenderText(char text, int numChars) {
		String textAsString = "" + text;
		String textToRender = this.getRenderText(textAsString, numChars);
		textAsString = null;
		return textToRender;
	}
	
	public void keyTyped(KeyEvent e) {
		this.lastCharacter = this.character;
		this.character = e.getKeyChar();
	}
}
