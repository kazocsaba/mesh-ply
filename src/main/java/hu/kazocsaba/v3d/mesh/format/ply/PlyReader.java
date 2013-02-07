package hu.kazocsaba.v3d.mesh.format.ply;

import java.awt.Color;
import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import hu.kazocsaba.math.matrix.MatrixFactory;
import hu.kazocsaba.math.matrix.Vector3;
import hu.kazocsaba.v3d.mesh.ColoredPointList;
import hu.kazocsaba.v3d.mesh.ColoredPointListImpl;
import hu.kazocsaba.v3d.mesh.IndexedTriangleMesh;
import hu.kazocsaba.v3d.mesh.IndexedTriangleMeshImpl;
import hu.kazocsaba.v3d.mesh.PointList;
import hu.kazocsaba.v3d.mesh.PointListImpl;

/**
 * Class for reading meshes from files in PLY format.
 * @author Kazó Csaba
 */
public final class PlyReader {
	private final List<Element> elements;
	
	private final Path file;
	
	// null means ascii
	private final ByteOrder fileFormat;
	
	private Element vertexElement=null;
	private int vertexXPropIndex=-1, vertexYPropIndex=-1, vertexZPropIndex=-1;
	private int vertexRedPropIndex=-1, vertexGreenPropIndex=-1, vertexBluePropIndex=-1;
	private Element faceElement=null;
	private int vertexIndicesPropIndex=-1;
	
	private final boolean hasVertices, hasVertexColors, hasFaces;
	
	/**
	 * Creates a new instance that reads data from the specified file. The constructor parses the header of the PLY file,
	 * and the user should query its contents with the {@link #hasVertices()}, {@link #hasFaces()} etc. functions before
	 * calling the appropriate reader method.
	 * @param file the file to read from
	 * @throws InvalidPlyFormatException if the file format is incorrect
	 * @throws IOException if an I/O error occurs
	 */
	public PlyReader(Path file) throws IOException, InvalidPlyFormatException {
		this.file=file;
		try (Scanner scanner=new Scanner(file, "US-ASCII")) {
			scanner.useLocale(Locale.ROOT);
			String line=scanner.nextLine();
			if (line==null || !line.equals("ply"))
				throw new InvalidPlyFormatException("File is not in PLY format");

			String format=null;
			String version=null;
			elements=new ArrayList<>();
			{ // parse header
				Element currentElement=null;
				while (true) {
					if (!scanner.hasNextLine()) {
						throw new InvalidPlyFormatException("Unexpected end of file");
					}
					line=scanner.nextLine();
					Scanner wordScanner=new Scanner(line);
					String keyword=wordScanner.next();
					if ("format".equals(keyword)) {
						format=wordScanner.next();
						version=wordScanner.next();
						if (wordScanner.hasNext()) throw new InvalidPlyFormatException("Invalid file format");
					} else if ("comment".equals(keyword))
						continue;
					else if ("element".equals(keyword)) {
						String name=wordScanner.next();
						int count=wordScanner.nextInt();
						if (count<0) throw new InvalidPlyFormatException("Element "+name+" has negative instances");
						if (wordScanner.hasNext()) throw new InvalidPlyFormatException("Invalid file format");
						currentElement=new Element(name, count);
						elements.add(currentElement);
					} else if ("property".equals(keyword)) {
						if (currentElement==null) throw new InvalidPlyFormatException("Property without element");
						Property property;
						String type=wordScanner.next();
						if ("list".equals(type)) {
							Type countType=parse(wordScanner.next());
							if (countType==Type.FLOAT || countType==Type.DOUBLE) throw new InvalidPlyFormatException("List element count type must be integral");
							Type elemType=parse(wordScanner.next());
							String name=wordScanner.next();
							if (wordScanner.hasNext()) throw new InvalidPlyFormatException("Invalid file format");
							property=new ListProperty(name, countType, elemType);
						} else {
							String name=wordScanner.next();
							Type scalarType=parse(type);
							property=new ScalarProperty(name, scalarType);
						}
						currentElement.properties.add(property);
					} else if ("obj_info".equals(keyword)) {
						// ignore
					} else if ("end_header".equals(keyword))
						break;
					else
						throw new InvalidPlyFormatException("Unrecognized keyword in header: "+keyword);
				}
			}
			if (format==null) throw new InvalidPlyFormatException("No format specification found in header");
			if (!"1.0".equals(version)) throw new InvalidPlyFormatException("Unknown format version: "+version);
			if ("ascii".equals(format)) {
				fileFormat=null;
			} else {
				if ("binary_big_endian".equals(format))
					fileFormat=ByteOrder.BIG_ENDIAN;
				else if ("binary_little_endian".equals(format))
					fileFormat=ByteOrder.LITTLE_ENDIAN;
				else
					throw new InvalidPlyFormatException("Invalid format: "+format);
			}
		}
		
		for (Element e: elements) {
			if ("vertex".equals(e.name)) {
				if (vertexElement!=null) throw new InvalidPlyFormatException("Multiple vertex elements");
				vertexElement=e;
				for (int pi=0; pi<e.properties.size(); pi++) {
					Property p=e.properties.get(pi);
					switch (p.name) {
						case "x":
							if (p instanceof ListProperty) throw new InvalidPlyFormatException("Invalid vertex.x property");
							if (vertexXPropIndex!=-1) throw new InvalidPlyFormatException("Multiple vertex.x properties");
							vertexXPropIndex=pi;
							break;
						case "y":
							if (p instanceof ListProperty) throw new InvalidPlyFormatException("Invalid vertex.y property");
							if (vertexYPropIndex!=-1) throw new InvalidPlyFormatException("Multiple vertex.y properties");
							vertexYPropIndex=pi;
							break;
						case "z":
							if (p instanceof ListProperty) throw new InvalidPlyFormatException("Invalid vertex.z property");
							if (vertexZPropIndex!=-1) throw new InvalidPlyFormatException("Multiple vertex.z properties");
							vertexZPropIndex=pi;
							break;
						case "red":
							if (p instanceof ListProperty) throw new InvalidPlyFormatException("Invalid vertex.red property");
							if (vertexRedPropIndex!=-1) throw new InvalidPlyFormatException("Multiple vertex.red properties");
							vertexRedPropIndex=pi;
							break;
						case "green":
							if (p instanceof ListProperty) throw new InvalidPlyFormatException("Invalid vertex.green property");
							if (vertexGreenPropIndex!=-1) throw new InvalidPlyFormatException("Multiple vertex.green properties");
							vertexGreenPropIndex=pi;
							break;
						case "blue":
							if (p instanceof ListProperty) throw new InvalidPlyFormatException("Invalid vertex.blue property");
							if (vertexBluePropIndex!=-1) throw new InvalidPlyFormatException("Multiple vertex.blue properties");
							vertexBluePropIndex=pi;
							break;
					}
				}
			} else if ("face".equals(e.name)) {
				if (faceElement!=null) throw new InvalidPlyFormatException("Multiple face elements");
				faceElement=e;
				for (int pi=0; pi<e.properties.size(); pi++) {
					Property p=e.properties.get(pi);
					if ("vertex_indices".equals(p.name)) {
						if (p instanceof ScalarProperty) throw new InvalidPlyFormatException("Face.vertex_indices property is not a list");
						if (((ListProperty)p).elemType==Type.FLOAT || ((ListProperty)p).elemType==Type.DOUBLE) throw new InvalidPlyFormatException("Face vertex indices must be integral");
						if (vertexIndicesPropIndex!=-1) throw new InvalidPlyFormatException("Multiple face.vertex_indices properties");
						vertexIndicesPropIndex=pi;
					}
				}
			}
		}
		hasVertices=vertexElement!=null;
		if (hasVertices) {
			if (vertexXPropIndex==-1) throw new InvalidPlyFormatException("No vertex.x property found");
			if (vertexYPropIndex==-1) throw new InvalidPlyFormatException("No vertex.y property found");
			if (vertexZPropIndex==-1) throw new InvalidPlyFormatException("No vertex.z property found");
			
		}
		
		hasVertexColors=vertexRedPropIndex!=-1 || vertexGreenPropIndex!=-1 || vertexBluePropIndex!=-1;
		if (hasVertexColors) {
			if (vertexRedPropIndex==-1 || vertexGreenPropIndex==-1 || vertexBluePropIndex==-1) throw new InvalidPlyFormatException("Incomplete vertex color");
		}
		
		hasFaces=faceElement!=null && faceElement.count>0;
		if (hasFaces) {
			if (!hasVertices) throw new InvalidPlyFormatException("Faces without vertices");
			if (vertexIndicesPropIndex==-1) throw new InvalidPlyFormatException("No face.vertex_indices property found");
		}
	}

	/**
	 * Returns whether the PLY file contains vertex data.
	 * @return {@code true} if the file contains vertices
	 */
	public boolean hasVertices() {
		return hasVertices;
	}
	/**
	 * Returns whether the PLY file contains colored vertex data.
	 * @return {@code true} if the file contains vertices along with vertex colors
	 */
	public boolean hasVertexColors() {
		return hasVertexColors;
	}
	/**
	 * Returns whether the PLY file contains a mesh.
	 * @return {@code true} if the file contains mesh data (vertices and faces)
	 */
	public boolean hasFaces() {
		return hasFaces;
	}
	private Input getInput() throws IOException {
		if (fileFormat==null) {
			return new AsciiInput(Files.newInputStream(file));
		} else {
			return new BinaryInput(Files.newByteChannel(file, StandardOpenOption.READ), fileFormat);
		}
	}
	/**
	 * Reads vertices from the file.
	 * @return the vertices defined by this file as a point list
	 * @throws IOException if an I/O error occurs
	 * @throws InvalidPlyFormatException if the file format is incorrect
	 * @throws IllegalStateException if the file does not contain vertex data
	 */
	public PointList readVertices() throws IOException, InvalidPlyFormatException {
		if (!hasVertices) throw new IllegalStateException("No vertices");
		if (hasVertexColors) return readColoredVertices();
		
		Input input=getInput();
		
		List<Vector3> vertices=new ArrayList<>(vertexElement.count);

		for (Element currentElement: elements) {
			if (currentElement==vertexElement) {
				/* Parse vertices */
				for (int elemIndex=0; elemIndex<currentElement.count; elemIndex++) {
					Vector3 v=MatrixFactory.createVector3();
					vertices.add(v);
					for (int propIndex=0; propIndex<currentElement.properties.size(); propIndex++) {
						Property prop=currentElement.properties.get(propIndex);
						if (propIndex==vertexXPropIndex) {
							v.setX(input.read(((ScalarProperty)prop).type).doubleValue());
						} else if (propIndex==vertexYPropIndex) {
							v.setY(input.read(((ScalarProperty)prop).type).doubleValue());
						} else if (propIndex==vertexZPropIndex) {
							v.setZ(input.read(((ScalarProperty)prop).type).doubleValue());
						} else {
							// ignore any other property
							if (prop instanceof ListProperty) {
								int count=input.read(((ListProperty)prop).countType).intValue();
								if (count<0) throw new InvalidPlyFormatException("List with negative number of elements");
								for (int i=0; i<count; i++) {
									input.read(((ListProperty)prop).elemType);
								}
							} else {
								input.read(((ScalarProperty)prop).type);
							}
						}
					}
				}
			} else {
				/* Parse anything else */
				for (int elemIndex=0; elemIndex<currentElement.count; elemIndex++) {
					for (int propIndex=0; propIndex<currentElement.properties.size(); propIndex++) {
						Property prop=currentElement.properties.get(propIndex);
						if (prop instanceof ListProperty) {
							int count=input.read(((ListProperty)prop).countType).intValue();
							if (count<0) throw new InvalidPlyFormatException("List with negative number of elements");
							for (int i=0; i<count; i++) {
								input.read(((ListProperty)prop).elemType);
							}
						} else {
							input.read(((ScalarProperty)prop).type);
						}
					}
				}
			}
		}
		input.needEnd();

		return new PointListImpl(vertices);
	}
	/**
	 * Reads colored vertices from the file.
	 * @return the vertices defined by this file as a colored point list
	 * @throws IOException if an I/O error occurs
	 * @throws InvalidPlyFormatException if the file format is incorrect
	 * @throws IllegalStateException if the file does not contain colored vertex data
	 */
	public ColoredPointList readColoredVertices() throws IOException, InvalidPlyFormatException {
		if (!hasVertices) throw new IllegalStateException("No vertices");
		if (!hasVertexColors) throw new IllegalStateException("No vertex colors");
		
		List<Vector3> vertices=new ArrayList<>(vertexElement.count);
		List<Color> colors=new ArrayList<>(vertexElement.count);
		
		try (Input input=getInput()) {

			for (Element currentElement: elements) {
				if (currentElement==vertexElement) {
					/* Parse vertices */
					int red=-1, green=-1, blue=-1;
					for (int elemIndex=0; elemIndex<currentElement.count; elemIndex++) {
						Vector3 v=MatrixFactory.createVector3();
						vertices.add(v);
						for (int propIndex=0; propIndex<currentElement.properties.size(); propIndex++) {
							Property prop=currentElement.properties.get(propIndex);
							if (propIndex==vertexXPropIndex) {
								v.setX(input.read(((ScalarProperty)prop).type).doubleValue());
							} else if (propIndex==vertexYPropIndex) {
								v.setY(input.read(((ScalarProperty)prop).type).doubleValue());
							} else if (propIndex==vertexZPropIndex) {
								v.setZ(input.read(((ScalarProperty)prop).type).doubleValue());
							} else if (propIndex==vertexRedPropIndex) {
								red=input.read(((ScalarProperty)prop).type).intValue();
							} else if (propIndex==vertexGreenPropIndex) {
								green=input.read(((ScalarProperty)prop).type).intValue();
							} else if (propIndex==vertexBluePropIndex) {
								blue=input.read(((ScalarProperty)prop).type).intValue();
							} else {
								// ignore any other property
								if (prop instanceof ListProperty) {
									int count=input.read(((ListProperty)prop).countType).intValue();
									if (count<0) throw new InvalidPlyFormatException("List with negative number of elements");
									for (int i=0; i<count; i++) {
										input.read(((ListProperty)prop).elemType);
									}
								} else {
									input.read(((ScalarProperty)prop).type);
								}
							}
						}
						colors.add(new Color(red, green, blue));
					}
				} else {
					/* Parse anything else */
					for (int elemIndex=0; elemIndex<currentElement.count; elemIndex++) {
						for (int propIndex=0; propIndex<currentElement.properties.size(); propIndex++) {
							Property prop=currentElement.properties.get(propIndex);
							if (prop instanceof ListProperty) {
								int count=input.read(((ListProperty)prop).countType).intValue();
								if (count<0) throw new InvalidPlyFormatException("List with negative number of elements");
								for (int i=0; i<count; i++) {
									input.read(((ListProperty)prop).elemType);
								}
							} else {
								input.read(((ScalarProperty)prop).type);
							}
						}
					}
				}
			}
			input.needEnd();
		}
		
		return new ColoredPointListImpl(vertices, colors);
	}
	
	/**
	 * Reads a mesh from this file.
	 * @return the mesh contained in the file
	 * @throws IOException if an I/O error occurs
	 * @throws InvalidPlyFormatException if the format of the file is incorrect
	 * @throws IllegalStateException if the file doesn't contain any faces ({@link #hasFaces()} returns {@code false})
	 */
	public IndexedTriangleMesh readMesh() throws IOException, InvalidPlyFormatException {
		if (!hasFaces) throw new IllegalStateException("No faces");
		
		List<Vector3> vertices=new ArrayList<>(vertexElement.count);
		List<int[]> triangles=new ArrayList<>(faceElement.count);
		
		try (Input input=getInput()) {
		
			for (Element currentElement: elements) {
				if (currentElement==vertexElement) {
					/* Parse vertices */
					for (int elemIndex=0; elemIndex<currentElement.count; elemIndex++) {
						Vector3 v=MatrixFactory.createVector3();
						vertices.add(v);
						for (int propIndex=0; propIndex<currentElement.properties.size(); propIndex++) {
							Property prop=currentElement.properties.get(propIndex);
							if (propIndex==vertexXPropIndex) {
								v.setX(input.read(((ScalarProperty)prop).type).doubleValue());
							} else if (propIndex==vertexYPropIndex) {
								v.setY(input.read(((ScalarProperty)prop).type).doubleValue());
							} else if (propIndex==vertexZPropIndex) {
								v.setZ(input.read(((ScalarProperty)prop).type).doubleValue());
							} else {
								if (prop instanceof ListProperty) {
									int count=input.read(((ListProperty)prop).countType).intValue();
									if (count<0) throw new InvalidPlyFormatException("List with negative number of elements");
									for (int i=0; i<count; i++) {
										input.read(((ListProperty)prop).elemType);
									}
								} else {
									input.read(((ScalarProperty)prop).type);
								}
							}
						}
					}
				} else if (currentElement==faceElement) {
					/* Parse faces */
					for (int elemIndex=0; elemIndex<currentElement.count; elemIndex++) {
						for (int propIndex=0; propIndex<currentElement.properties.size(); propIndex++) {
							Property prop=currentElement.properties.get(propIndex);
							if (propIndex==vertexIndicesPropIndex) {
								ListProperty lp=(ListProperty)prop;
								int count=input.read(lp.countType).intValue();
								if (count<3) throw new InvalidPlyFormatException("Face with "+count+" vertices");
								switch (count) {
									case 3:
										Number v1,v2,v3,v4;
										v1=input.read(lp.elemType);
										if (v1.longValue()<0 || v1.longValue()>=vertexElement.count) throw new InvalidPlyFormatException("Invalid vertex index: "+v1.longValue());
										v2=input.read(lp.elemType);
										if (v2.longValue()<0 || v2.longValue()>=vertexElement.count) throw new InvalidPlyFormatException("Invalid vertex index: "+v2.longValue());
										v3=input.read(lp.elemType);
										if (v3.longValue()<0 || v3.longValue()>=vertexElement.count) throw new InvalidPlyFormatException("Invalid vertex index: "+v3.longValue());
										triangles.add(new int[]{v1.intValue(), v2.intValue(), v3.intValue()});
										break;
									case 4:
										v1=input.read(lp.elemType);
										if (v1.longValue()<0 || v1.longValue()>=vertexElement.count) throw new InvalidPlyFormatException("Invalid vertex index: "+v1.longValue());
										v2=input.read(lp.elemType);
										if (v2.longValue()<0 || v2.longValue()>=vertexElement.count) throw new InvalidPlyFormatException("Invalid vertex index: "+v2.longValue());
										v3=input.read(lp.elemType);
										if (v3.longValue()<0 || v3.longValue()>=vertexElement.count) throw new InvalidPlyFormatException("Invalid vertex index: "+v3.longValue());
										v4=input.read(lp.elemType);
										if (v4.longValue()<0 || v4.longValue()>=vertexElement.count) throw new InvalidPlyFormatException("Invalid vertex index: "+v4.longValue());
										triangles.add(new int[]{v1.intValue(), v2.intValue(), v3.intValue()});
										triangles.add(new int[]{v1.intValue(), v3.intValue(), v4.intValue()});
										break;
									default:
										throw new InvalidPlyFormatException("Cannot handle faces with more than 4 vertices");
								}
							} else if (prop instanceof ListProperty) {
								int count=input.read(((ListProperty)prop).countType).intValue();
								if (count<0) throw new InvalidPlyFormatException("List with negative number of elements");
								for (int i=0; i<count; i++) {
									input.read(((ListProperty)prop).elemType);
								}
							} else {
								input.read(((ScalarProperty)prop).type);
							}
						}
					}
				} else {
					/* Parse anything else */
					for (int elemIndex=0; elemIndex<currentElement.count; elemIndex++) {
						for (int propIndex=0; propIndex<currentElement.properties.size(); propIndex++) {
							Property prop=currentElement.properties.get(propIndex);
							if (prop instanceof ListProperty) {
								int count=input.read(((ListProperty)prop).countType).intValue();
								if (count<0) throw new InvalidPlyFormatException("List with negative number of elements");
								for (int i=0; i<count; i++) {
									input.read(((ListProperty)prop).elemType);
								}
							} else {
								input.read(((ScalarProperty)prop).type);
							}
						}
					}
				}
			}
			input.needEnd();
		}
		return new IndexedTriangleMeshImpl(vertices, triangles);
	}
	
	interface Input extends Closeable {
		public Number read(Type type) throws IOException;
		public void needEnd() throws IOException;
	}
	private static class AsciiInput implements Input {
		private final Scanner scanner;

		public AsciiInput(InputStream in) throws IOException {
			scanner = new Scanner(new BufferedInputStream(in), "US-ASCII");
			
			// skip the header
			String line;
			do {
				line=scanner.nextLine();
			} while (!"end_header".equals(line));
		}

		@Override
		public Number read(Type type) throws IOException {
			return type.parse(scanner);
		}

		@Override
		public void needEnd() throws IOException {
			if (scanner.hasNext())
				throw new InvalidPlyFormatException("Invalid file format: expected end of file, found "+scanner.next());
		}

		@Override
		public void close() throws IOException {
			scanner.close();
		}
		
	}
	private static class BinaryInput implements Input {
		private final ReadableByteChannel channel;
		private final ByteBuffer buffer;
		private int bufferLength;

		public BinaryInput(ReadableByteChannel channel, ByteOrder byteOrder) throws IOException {
			final byte[] END="end_header".getBytes("US-ASCII");
			byte[] endTest=new byte[END.length];

			this.channel=channel;
			buffer=ByteBuffer.allocate(8192).order(byteOrder);
			bufferLength=0;
			// skip header
			int lineStart=0;
			int read;
			while (true) {
				read=channel.read(buffer);
				if (read==-1) throw new InvalidPlyFormatException("Cannot find the end of the header on the second pass: file has been modified");
				bufferLength+=read;
				for (int i=bufferLength-read; i<bufferLength; i++) {
					if (buffer.get(i)==(byte)'\n') {
						int length=i-lineStart;
						if (length==END.length) {
							buffer.position(lineStart);
							buffer.get(endTest);
							buffer.get(); // skip the '\n'
							if (Arrays.equals(END, endTest)) {
								// done skipping header
								buffer.limit(bufferLength);
								buffer.compact();
								buffer.flip();
								return;
							}
						}
						lineStart=i+1;
					}
				}
				if (buffer.remaining()==0) {
					if (lineStart==0) throw new InvalidPlyFormatException("Line too long");
					buffer.position(lineStart);
					buffer.limit(bufferLength);
					buffer.compact();
					bufferLength-=lineStart;
					lineStart=0;
					buffer.limit(buffer.capacity());
				}
			}
		}

		@Override
		public Number read(Type type) throws IOException {
			while (true) {
				try {
					return type.read(buffer);
				} catch (BufferUnderflowException e) {}
				int position=buffer.position();
				int limit=buffer.limit();
				
				if (position>buffer.capacity()-20) {
					buffer.compact();
					limit=limit-position;
					position=0;
				}

				buffer.limit(buffer.capacity());
				buffer.position(limit);
				int read=channel.read(buffer);
				if (read==-1) throw new InvalidPlyFormatException("Unexpected end of file");
				if (read==0) throw new AssertionError();
				buffer.limit(limit+read);
				buffer.position(position);
			}
		}

		@Override
		public void needEnd() throws IOException {
			if (buffer.remaining()!=0) throw new InvalidPlyFormatException("Expected end of file");
			buffer.position(0);
			buffer.limit(1);
			if (channel.read(buffer)!=-1) throw new InvalidPlyFormatException("Expected end of file");
		}

		@Override
		public void close() throws IOException {
			channel.close();
		}

	}
	/**
	 * Reads a mesh from a PLY file.
	 * @param file the file to read from
	 * @return the mesh contained in the file
	 * @throws IOException if an I/O error occurs
	 * @throws InvalidPlyFormatException if the format of the file is incorrect or no mesh is found
	 */
	public static IndexedTriangleMesh readMesh(File file) throws IOException, InvalidPlyFormatException {
		PlyReader reader=new PlyReader(file.toPath());
		if (!reader.hasFaces()) throw new InvalidPlyFormatException("No faces found");
		
		return reader.readMesh();
	}
	
	private static Type parse(String type) throws InvalidPlyFormatException {
		if (type.equals("char")) return Type.CHAR;
		if (type.equals("uchar")) return Type.UCHAR;
		if (type.equals("short")) return Type.SHORT;
		if (type.equals("ushort")) return Type.USHORT;
		if (type.equals("int")) return Type.INT;
		if (type.equals("uint")) return Type.UINT;
		if (type.equals("float")) return Type.FLOAT;
		if (type.equals("double")) return Type.DOUBLE;
		throw new InvalidPlyFormatException("Unrecognized type: "+type);
	}
}
