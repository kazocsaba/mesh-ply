package kcsaba.vision.data.format.ply;

/**
 *
 * @author Kazó Csaba
 */
class ListProperty extends Property {
	public Type countType;
	public Type elemType;

	public ListProperty(String name, Type countType, Type elemType) {
		super(name);
		this.countType = countType;
		this.elemType = elemType;
	}

}
