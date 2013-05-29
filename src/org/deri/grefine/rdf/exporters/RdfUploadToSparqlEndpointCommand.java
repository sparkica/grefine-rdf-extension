package org.deri.grefine.rdf.exporters;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.refine.browsing.Engine;
import com.google.refine.browsing.FilteredRows;
import com.google.refine.browsing.RowVisitor;
import com.google.refine.exporters.UrlExporter;
import com.google.refine.model.Project;
import com.google.refine.model.Row;

import org.deri.grefine.rdf.Node;
import org.deri.grefine.rdf.RdfSchema;
import org.deri.grefine.rdf.Util;
import org.deri.grefine.rdf.app.ApplicationContext;
import org.deri.grefine.rdf.commands.RdfCommand;
import org.deri.grefine.rdf.vocab.VocabularyIndexException;
import org.deri.grefine.reconcile.rdf.endpoints.QueryEndpoint;
import org.deri.grefine.reconcile.rdf.endpoints.QueryEndpointImpl;
import org.deri.grefine.reconcile.rdf.executors.QueryExecutor;
import org.deri.grefine.reconcile.rdf.executors.VirtuosoRemoteQueryExecutor;
import org.deri.grefine.reconcile.rdf.factories.SparqlQueryFactory;
import org.deri.grefine.reconcile.rdf.factories.VirtuosoSparqlQueryFactory;
import org.openrdf.model.BNode;
import org.openrdf.model.ValueFactory;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.rio.RDFFormat;
import org.openrdf.sail.memory.MemoryStore;


//should it be a command or operation?
public class RdfUploadToSparqlEndpointCommand extends RdfCommand {

	private RDFFormat format;

	public RdfUploadToSparqlEndpointCommand(ApplicationContext ctxt) {
		super(ctxt);
	}
	
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
  
    	String graph = request.getParameter("graph");
    	String endpoint = request.getParameter("endpoint");
    	String query = SPARQL_INSERT_TEMPLATE.replace("[[GRAPH]]", graph);
    	
    }


	public Repository buildModel(final Project project, Engine engine, RdfSchema schema) throws IOException{
		RdfRowVisitor visitor = new RdfRowVisitor(schema) {

			@Override
			public boolean visit(Project project, int rowIndex, Row row) {
				for(Node root:roots){
					root.createNode(baseUri, factory, con, project, row, rowIndex,blanks);
				}
				return false;
			}
		};
		Repository model = buildModel(project, engine,visitor);

		return model;
	}

	public static Repository buildModel(Project project, Engine engine, RdfRowVisitor visitor) {
		FilteredRows filteredRows = engine.getAllFilteredRows();
		filteredRows.accept(project, visitor);
		return visitor.getModel();

	}

	//TODO: decide which format you want to support
	//TODO: figure out which formats are supported by Virtuoso
	public String getContentType() {

		if(format.equals(RDFFormat.TURTLE)){
			return "text/turtle";
		}else{
			return "application/rdf+xml";
		}
	}


	public static abstract class RdfRowVisitor implements RowVisitor{
		protected Repository model;
		protected URI baseUri;
		protected BNode[] blanks;
		protected List<Node> roots;
		private RdfSchema schema;

		protected ValueFactory factory;
		protected RepositoryConnection con;

		public Repository getModel() {
			return model;
		}

		public RdfRowVisitor(RdfSchema schema){
			this.schema = schema;
			baseUri = schema.getBaseUri();
			roots = schema.getRoots();

			//initilaizing repository
			model = new SailRepository(new MemoryStore());
			try{
				model.initialize();
				RepositoryConnection con = model.getConnection();
				try{
					ValueFactory factory = con.getValueFactory();
					blanks = new BNode[schema.get_blanks().size()];
					for (int i = 0; i < blanks.length; i++) {
						blanks[i] = factory.createBNode();
					}
				}finally{
					con.close();
				}
			}catch(RepositoryException ex){
				throw new RuntimeException(ex);
			}
		}
		public void end(Project project) {
			try {
				if(con.isOpen()){
					con.close();
				}
			} catch (RepositoryException e) {
				throw new RuntimeException("",e);
			}
		}

		public void start(Project project) {
			try{
				con = model.getConnection();
				factory = con.getValueFactory();
			}catch(RepositoryException ex){
				throw new RuntimeException("",ex);
			}
		}

		abstract public boolean visit(Project project, int rowIndex, Row row);
		public RdfSchema getRdfSchema(){
			return schema;
		}
	}

	private static final String SPARQL_INSERT_TEMPLATE = "INSERT INTO GRAPH [[GRAPH]]" +
														"{" + 
															"[[DATA_TRIPLES]]" +
														"}";

}
