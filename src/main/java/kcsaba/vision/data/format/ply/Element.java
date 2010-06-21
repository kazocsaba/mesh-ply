package kcsaba.vision.data.format.ply;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Kazó Csaba
 */
class Element {
	public String name;
	public int count;
	public List<Property> properties=new ArrayList<Property>();

	public Element(String name, int count) {
		this.name = name;
		this.count = count;
	}
	
}
