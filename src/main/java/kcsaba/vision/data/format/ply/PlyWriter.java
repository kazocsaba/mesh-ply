package kcsaba.vision.data.format.ply;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.Writer;
import kcsaba.math.matrix.Vector3;
import kcsaba.vision.data.mesh.IndexedTriangleMesh;
import kcsaba.vision.data.mesh.Meshes;
import kcsaba.vision.data.mesh.TriangleMesh;

/**
 * A writer for saving meshes in PLY format.
 * @author Kazó Csaba
 */
public class PlyWriter {
	private boolean binary=false;
	private boolean verticesAsFloats=false;
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
	 * Writes a mesh to a file in PLY format.
	 * @param mesh the mesh to write
	 * @param comment optional multi-line comment to write to the output; can be {@code null}
	 * @param output the file to write to
	 * @throws IOException if an I/O error occurs
	 */
	public synchronized void write(TriangleMesh mesh, String comment, File output) throws IOException {
		IndexedTriangleMesh indexedMesh=Meshes.toIndexed(mesh);
		BufferedOutputStream bos=new BufferedOutputStream(new FileOutputStream(output));
		try {
			// header
			{
				Writer writer=new OutputStreamWriter(bos, "UTF-8");
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
				writeBinary(indexedMesh, bos);
			else
				writeAscii(indexedMesh, bos);
		} finally {
			try {bos.close();} catch (IOException e) {}
		}
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
