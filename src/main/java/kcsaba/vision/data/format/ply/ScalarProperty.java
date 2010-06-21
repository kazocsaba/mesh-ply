package kcsaba.vision.data.format.ply;

/**
 *
 * @author Kazó Csaba
 */
class ScalarProperty extends Property {
	public Type type;

	public ScalarProperty(String name, Type type) {
		super(name);
		this.type = type;
	}

}
