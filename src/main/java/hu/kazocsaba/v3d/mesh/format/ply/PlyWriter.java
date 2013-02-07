package hu.kazocsaba.v3d.mesh.format.ply;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import hu.kazocsaba.math.matrix.Vector3;
import hu.kazocsaba.v3d.mesh.ColoredPointList;
import hu.kazocsaba.v3d.mesh.IndexedTriangleMesh;
import hu.kazocsaba.v3d.mesh.Meshes;
import hu.kazocsaba.v3d.mesh.PointList;
import hu.kazocsaba.v3d.mesh.TriangleMesh;

/**
 * A writer for saving meshes in PLY format.
 * @author Kazó Csaba
 */
public class PlyWriter {
	private boolean binary=false;
	private boolean verticesAsFloats=false;
	
	/**
	 * Creates a new writer instance. By default, it is set to create binary format and to store vertex coordinates
	 * as doubles.
	 */
	public PlyWriter() {}

	/**
	 * Sets whether this writer creates a binary PLY file.
	 * @param binary if {@code true}, then a binary file will be written
	 * @return this writer
	 */
	public synchronized PlyWriter setBinary(boolean binary) {
		this.binary = binary;
		return this;
	}
	
	/**
	 * Sets whether the vertex coordinates should be written as floats. If the argument is {@code false}, they
	 * are written as doubles.
	 * @param verticesAsFloats whether the coordinates should be written as 32-bit floating point numbers
	 * @return this writer
	 */
	public synchronized PlyWriter setVerticesAsFloats(boolean verticesAsFloats) {
		this.verticesAsFloats = verticesAsFloats;
		return this;
	}

	/**
	 * Writes a point list to a file in PLY format. If {@code points} also implements {@link ColoredPointList}, the
	 * vertex colors will also be written.
	 * @param points the vertices
	 * @param comment an optional sequence of comments separated by new line characters that will be included
	 * in the header of the PLY file
	 * @param output the file to write to
	 * @throws IOException if an I/O error occurs
	 */
	public synchronized void write(PointList points, String comment, Path output) throws IOException {
		try (BufferedOutputStream bos=new BufferedOutputStream(Files.newOutputStream(output))) {
			write(points, comment, bos);
		}
	}
	/**
	 * Writes a point list to a stream in PLY format. If {@code points} also implements {@link ColoredPointList}, the
	 * vertex colors will also be written.
	 * @param points the vertices
	 * @param comment an optional sequence of comments separated by new line characters that will be included
	 * in the header of the PLY file
	 * @param out the stream to write to
	 * @throws IOException if an I/O error occurs
	 */
	public synchronized void write(PointList points, String comment, OutputStream out) throws IOException {
		// header
		{
			Writer writer=new OutputStreamWriter(out);
			writer.write("ply\n");
			writer.write("format ");
			writer.write(binary ? "binary_big_endian" : "ascii");
			writer.write(" 1.0\n");
			if (comment!=null) {
				BufferedReader r=new BufferedReader(new StringReader(comment));
				String commentLine;
				while ((commentLine=r.readLine())!=null) {
					writer.write("comment ");
					writer.write(commentLine);
					writer.write('\n');
				}
			}
			writer.write("element vertex "+points.getPointCount()+"\n");
			writer.write("property "+(verticesAsFloats ? "float" : "double")+" x\n");
			writer.write("property "+(verticesAsFloats ? "float" : "double")+" y\n");
			writer.write("property "+(verticesAsFloats ? "float" : "double")+" z\n");
			if (points instanceof ColoredPointList) {
				writer.write("property uchar red\n");
				writer.write("property uchar green\n");
				writer.write("property uchar blue\n");
			}
			writer.write("element face 0\n");
			writer.write("end_header\n");
			writer.flush();
		}
		// body
		if (binary)
			writeBinary(points, out);
		else
			writeAscii(points, out);
	}
	/**
	 * Writes a mesh to a file in PLY format.
	 * @param mesh the mesh to write
	 * @param comment optional multi-line comment to write to the output; can be {@code null}
	 * @param output the file to write to
	 * @throws IOException if an I/O error occurs
	 */
	public synchronized void write(TriangleMesh mesh, String comment, Path output) throws IOException {
		try (BufferedOutputStream bos=new BufferedOutputStream(Files.newOutputStream(output))) {
			write(mesh, comment, bos);
		}
	}
	/**
	 * Writes a mesh to a stream in PLY format.
	 * @param mesh the mesh to write
	 * @param comment optional multi-line comment to write to the output; can be {@code null}
	 * @param out the stream to write to
	 * @throws IOException if an I/O error occurs
	 */
	public synchronized void write(TriangleMesh mesh, String comment, OutputStream out) throws IOException {
		IndexedTriangleMesh indexedMesh=Meshes.toIndexed(mesh);
		// header
		{
			Writer writer=new OutputStreamWriter(out, "UTF-8");
			writer.write("ply\n");
			writer.write("format ");
			writer.write(binary ? "binary_big_endian" : "ascii");
			writer.write(" 1.0\n");
			if (comment!=null) {
				BufferedReader r=new BufferedReader(new StringReader(comment));
				String commentLine;
				while ((commentLine=r.readLine())!=null) {
					writer.write("comment ");
					writer.write(commentLine);
					writer.write('\n');
				}
			}
			writer.write("element vertex "+indexedMesh.getPointCount()+"\n");
			writer.write("property "+(verticesAsFloats ? "float" : "double")+" x\n");
			writer.write("property "+(verticesAsFloats ? "float" : "double")+" y\n");
			writer.write("property "+(verticesAsFloats ? "float" : "double")+" z\n");
			writer.write("element face "+indexedMesh.getTriangleCount()+"\n");
			writer.write("property list uchar int vertex_indices\n");
			writer.write("end_header\n");
			writer.flush();
		}
		// body
		if (binary)
			writeBinary(indexedMesh, out);
		else
			writeAscii(indexedMesh, out);
	}
	private void writeBinary(PointList points, OutputStream bos) throws IOException {
		DataOutputStream dos=new DataOutputStream(bos);
		if (verticesAsFloats) {
			for (int i=0; i<points.getPointCount(); i++) {
				Vector3 p=points.getPoint(i);
				dos.writeFloat((float)p.getX());
				dos.writeFloat((float)p.getY());
				dos.writeFloat((float)p.getZ());
				if (points instanceof ColoredPointList) {
					ColoredPointList cpl=(ColoredPointList)points;
					dos.write(cpl.getPointColor(i).getRed());
					dos.write(cpl.getPointColor(i).getGreen());
					dos.write(cpl.getPointColor(i).getBlue());
				}
			}
		} else {
			for (int i=0; i<points.getPointCount(); i++) {
				Vector3 p=points.getPoint(i);
				dos.writeDouble(p.getX());
				dos.writeDouble(p.getY());
				dos.writeDouble(p.getZ());
				if (points instanceof ColoredPointList) {
					ColoredPointList cpl=(ColoredPointList)points;
					dos.write(cpl.getPointColor(i).getRed());
					dos.write(cpl.getPointColor(i).getGreen());
					dos.write(cpl.getPointColor(i).getBlue());
				}
			}
		}
		dos.flush();
	}
	private void writeAscii(PointList points, OutputStream bos) throws IOException {
		Writer writer=new OutputStreamWriter(bos, "UTF-8");
		if (verticesAsFloats) {
			for (int i=0; i<points.getPointCount(); i++) {
				Vector3 p=points.getPoint(i);
				writer.write(Float.toString((float)p.getX()));
				writer.write(' ');
				writer.write(Float.toString((float)p.getY()));
				writer.write(' ');
				writer.write(Float.toString((float)p.getZ()));
				if (points instanceof ColoredPointList) {
					ColoredPointList cpl=(ColoredPointList)points;
					writer.write(' ');
					writer.write(Integer.toString(cpl.getPointColor(i).getRed()));
					writer.write(' ');
					writer.write(Integer.toString(cpl.getPointColor(i).getGreen()));
					writer.write(' ');
					writer.write(Integer.toString(cpl.getPointColor(i).getBlue()));
				}
				writer.write('\n');
			}
		} else {
			for (int i=0; i<points.getPointCount(); i++) {
				Vector3 p=points.getPoint(i);
				writer.write(Double.toString(p.getX()));
				writer.write(' ');
				writer.write(Double.toString(p.getY()));
				writer.write(' ');
				writer.write(Double.toString(p.getZ()));
				if (points instanceof ColoredPointList) {
					ColoredPointList cpl=(ColoredPointList)points;
					writer.write(' ');
					writer.write(Integer.toString(cpl.getPointColor(i).getRed()));
					writer.write(' ');
					writer.write(Integer.toString(cpl.getPointColor(i).getGreen()));
					writer.write(' ');
					writer.write(Integer.toString(cpl.getPointColor(i).getBlue()));
				}
				writer.write('\n');
			}
		}
		writer.flush();
	}
	private void writeBinary(IndexedTriangleMesh indexedMesh, OutputStream bos) throws IOException {
		DataOutputStream dos=new DataOutputStream(bos);
		if (verticesAsFloats) {
			for (int i=0; i<indexedMesh.getPointCount(); i++) {
				Vector3 p=indexedMesh.getPoint(i);
				dos.writeFloat((float)p.getX());
				dos.writeFloat((float)p.getY());
				dos.writeFloat((float)p.getZ());
			}
		} else {
			for (int i=0; i<indexedMesh.getPointCount(); i++) {
				Vector3 p=indexedMesh.getPoint(i);
				dos.writeDouble(p.getX());
				dos.writeDouble(p.getY());
				dos.writeDouble(p.getZ());
			}
		}
		for (int i=0; i<indexedMesh.getTriangleCount(); i++) {
			dos.writeByte(3);
			dos.writeInt(indexedMesh.getTrianglePointIndex(i, 0));
			dos.writeInt(indexedMesh.getTrianglePointIndex(i, 1));
			dos.writeInt(indexedMesh.getTrianglePointIndex(i, 2));
		}
		dos.flush();
	}
	private void writeAscii(IndexedTriangleMesh indexedMesh, OutputStream bos) throws IOException {
		Writer writer=new OutputStreamWriter(bos, "UTF-8");
		if (verticesAsFloats) {
			for (int i=0; i<indexedMesh.getPointCount(); i++) {
				Vector3 p=indexedMesh.getPoint(i);
				writer.write(Float.toString((float)p.getX()));
				writer.write(' ');
				writer.write(Float.toString((float)p.getY()));
				writer.write(' ');
				writer.write(Float.toString((float)p.getZ()));
				writer.write('\n');
			}
		} else {
			for (int i=0; i<indexedMesh.getPointCount(); i++) {
				Vector3 p=indexedMesh.getPoint(i);
				writer.write(Double.toString(p.getX()));
				writer.write(' ');
				writer.write(Double.toString(p.getY()));
				writer.write(' ');
				writer.write(Double.toString(p.getZ()));
				writer.write('\n');
			}
		}
		for (int i=0; i<indexedMesh.getTriangleCount(); i++) {
			writer.write("3 ");
			writer.write(Integer.toString(indexedMesh.getTrianglePointIndex(i, 0)));
			writer.write(' ');
			writer.write(Integer.toString(indexedMesh.getTrianglePointIndex(i, 1)));
			writer.write(' ');
			writer.write(Integer.toString(indexedMesh.getTrianglePointIndex(i, 2)));
			writer.write('\n');
		}
		writer.flush();
	}
}
