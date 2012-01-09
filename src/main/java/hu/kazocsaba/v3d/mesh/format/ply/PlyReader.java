package hu.kazocsaba.v3d.mesh.format.ply;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import hu.kazocsaba.math.matrix.MatrixFactory;
import hu.kazocsaba.math.matrix.Vector3;
import hu.kazocsaba.v3d.mesh.IndexedTriangleMesh;
import hu.kazocsaba.v3d.mesh.IndexedTriangleMeshImpl;

/**
 * Class for reading meshes from files in PLY format.
 * @author Kazó Csaba
 */
public class PlyReader {
	private PlyReader() {}

	private interface Input {
		public Number read(Type type) throws IOException;
		public void needEnd() throws IOException;
	}
	private static class ScannerInput implements Input {
		private final Scanner scanner;

		public ScannerInput(Scanner scanner) {
			this.scanner = scanner;
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
	}
	private static class BinaryInput implements Input {
		private final FileChannel channel;
		private final ByteBuffer buffer;
		private int bufferLength;

		public BinaryInput(FileChannel channel, ByteOrder byteOrder) throws IOException {
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

	}
	/**
	 * Reads a mesh from a PLY file.
	 * @param file the file to read from
	 * @return the mesh contained in the file
	 * @throws IOException if an I/O error occurs
	 * @throws InvalidPlyFormatException if the format of the file is incorrect or no mesh is found
	 */
	public static IndexedTriangleMesh readMesh(File file) throws IOException, InvalidPlyFormatException {
		List<Vector3> vertices;
		List<int[]> triangles;
		FileInputStream fis=new FileInputStream(file);
		try {
			Scanner scanner=new Scanner(new BufferedInputStream(fis), "UTF-8");
			scanner.useLocale(Locale.ROOT);
			String line=scanner.nextLine();
			if (line==null || !line.equals("ply"))
				throw new InvalidPlyFormatException("File is not in PLY format");

			String format=null;
			String version=null;
			List<Element> elements=new ArrayList<Element>();
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
			Element vertexElement=null;
			int vertexXPropIndex=-1, vertexYPropIndex=-1, vertexZPropIndex=-1;
			Element faceElement=null;
			int vertexIndicesPropIndex=-1;
			for (Element e: elements)
				if ("vertex".equals(e.name)) {
					if (vertexElement!=null) throw new InvalidPlyFormatException("Multiple vertex elements");
					vertexElement=e;
					for (int pi=0; pi<e.properties.size(); pi++) {
						Property p=e.properties.get(pi);
						if ("x".equals(p.name)) {
							if (p instanceof ListProperty) throw new InvalidPlyFormatException("Invalid vertex.x property");
							if (vertexXPropIndex!=-1) throw new InvalidPlyFormatException("Multiple vertex.x properties");
							vertexXPropIndex=pi;
						} else if ("y".equals(p.name)) {
							if (p instanceof ListProperty) throw new InvalidPlyFormatException("Invalid vertex.y property");
							if (vertexYPropIndex!=-1) throw new InvalidPlyFormatException("Multiple vertex.x properties");
							vertexYPropIndex=pi;
						} else if ("z".equals(p.name)) {
							if (p instanceof ListProperty) throw new InvalidPlyFormatException("Invalid vertex.z propertyt");
							if (vertexZPropIndex!=-1) throw new InvalidPlyFormatException("Multiple vertex.x properties");
							vertexZPropIndex=pi;
						}
					}
				} else if ("face".equals(e.name)) {
					if (faceElement!=null) throw new InvalidPlyFormatException("Multiple face elements");
					faceElement=e;
					for (int pi=0; pi<e.properties.size(); pi++) {
						Property p=e.properties.get(pi);
						if ("vertex_indices".equals(p.name)) {
							if (p instanceof ScalarProperty) throw new InvalidPlyFormatException("Face.vertex_indices property is a list");
							if (((ListProperty)p).elemType==Type.FLOAT || ((ListProperty)p).elemType==Type.DOUBLE) throw new InvalidPlyFormatException("Face vertex indices must be integral");
							if (vertexIndicesPropIndex!=-1) throw new InvalidPlyFormatException("Multiple face.vertex_indices properties");
							vertexIndicesPropIndex=pi;
						}
					}
				}
			if (vertexElement==null) throw new InvalidPlyFormatException("No vertex element found");
			if (faceElement==null) throw new InvalidPlyFormatException("No face element found");
			if (vertexXPropIndex==-1) throw new InvalidPlyFormatException("No vertex.x property found");
			if (vertexYPropIndex==-1) throw new InvalidPlyFormatException("No vertex.y property found");
			if (vertexZPropIndex==-1) throw new InvalidPlyFormatException("No vertex.z property found");
			if (vertexIndicesPropIndex==-1) throw new InvalidPlyFormatException("No face.vertex_indices property found");
			vertices=new ArrayList<Vector3>(vertexElement.count);
			triangles=new ArrayList<int[]>(faceElement.count);
			Input input;
			if ("ascii".equals(format)) {
				input=new ScannerInput(scanner);
			} else {
				ByteOrder byteOrder;
				if ("binary_big_endian".equals(format))
					byteOrder=ByteOrder.BIG_ENDIAN;
				else if ("binary_little_endian".equals(format))
					byteOrder=ByteOrder.LITTLE_ENDIAN;
				else
					throw new InvalidPlyFormatException("Invalid format: "+format);
				FileChannel channel=fis.getChannel();
				channel.position(0);
				input=new BinaryInput(channel, byteOrder);
			}
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
		} finally {
			try {fis.close();} catch (IOException e) {}
		}
		return new IndexedTriangleMeshImpl(vertices, triangles);
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
