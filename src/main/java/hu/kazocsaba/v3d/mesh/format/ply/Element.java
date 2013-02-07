package hu.kazocsaba.v3d.mesh.format.ply;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Kazó Csaba
 */
class Element {
	public String name;
	public int count;
	public List<Property> properties=new ArrayList<>();

	public Element(String name, int count) {
		this.name = name;
		this.count = count;
	}
	
}
