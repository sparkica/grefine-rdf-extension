package org.deri.grefine.rdf.commands;

import java.io.IOException;
import java.io.StringWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.jena.riot.WebContent;
import org.apache.jena.riot.web.HttpOp;
import org.deri.grefine.rdf.Node;
import org.deri.grefine.rdf.RdfSchema;
import org.deri.grefine.rdf.app.ApplicationContext;
import org.deri.grefine.rdf.exporters.RdfExporter.RdfRowVisitor;
import org.deri.grefine.reconcile.rdf.executors.VirtuosoRemoteQueryExecutor;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFWriter;
import org.openrdf.rio.Rio;

import com.google.refine.browsing.Engine;
import com.google.refine.browsing.FilteredRows;
import com.google.refine.commands.Command;
import com.google.refine.model.Project;
import com.google.refine.model.Row;
import com.google.refine.util.ParsingUtilities;
import com.hp.hpl.jena.graph.Graph;
import com.hp.hpl.jena.graph.impl.GraphBase;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.sparql.graph.GraphSPARQLService;
import com.hp.hpl.jena.sparql.modify.UpdateProcessRemote;
import com.hp.hpl.jena.sparql.modify.UpdateProcessRemoteForm;
import com.hp.hpl.jena.sparql.modify.request.UpdateCreate;
import com.hp.hpl.jena.sparql.util.Context;
import com.hp.hpl.jena.update.GraphStore;
import com.hp.hpl.jena.update.GraphStoreFactory;
import com.hp.hpl.jena.update.UpdateAction;
import com.hp.hpl.jena.update.UpdateExecutionFactory;
import com.hp.hpl.jena.update.UpdateFactory;
import com.hp.hpl.jena.update.UpdateProcessor;
import com.hp.hpl.jena.update.UpdateRequest;

//Upload to Virtuoso
public class UploadRdfCommand extends Command {

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		try {
			Project project = getProject(request);
			Engine engine = getEngine(request, project);
			String params = request.getParameter("server");
			JSONObject o_params = ParsingUtilities.evaluateJsonStringToObject(params);

			String graph = o_params.getString("graph");
			String endpoint = o_params.getString("endpoint");

			response.setCharacterEncoding("UTF-8");
			response.setHeader("Content-Type", "application/json");

			String jsonString = request.getParameter("schema");
			JSONObject json = ParsingUtilities.evaluateJsonStringToObject(jsonString);
			final RdfSchema schema = RdfSchema.reconstruct(json);

			RdfRowVisitor visitor = new RdfRowVisitor(schema) {
				int _count;
				@Override
				public boolean visit(Project project, int rowIndex, Row row) {
					if(_count >= MAX_LIMIT_TRIPLES){
						return true;
					}
					for(Node root:roots){
						root.createNode(baseUri, factory, con, project, row, rowIndex,blanks);
					}
					_count +=1;
					return false;
				}
			};

			//local repository
			Repository model = buildModel(project, engine, visitor);
			
			//we are misusing select here, but cannot use ARQ update due to Virtuoso syntax 
			VirtuosoRemoteQueryExecutor executor = new VirtuosoRemoteQueryExecutor(endpoint, graph);
			String query = buildSparqlInsertQuery(graph, model, schema);
			
			
			ResultSet results = executor.sparql(query);

			//TODO: can I do this with an ordinary POST request?
			//TODO: i will probably need a factory for different endpoints
			//TODO: virtuoso does not have SPARQL11 syntax
			//TODO: add to schema - option to select wich nodes to export and upload
			for(;results.hasNext();) {
				QuerySolution sol = results.next();
				
				System.out.println(sol.toString());
			} 
			
			//String results = "just some results for testing";
			//TODO: parse these results into something useful
			//System.out.println(results.toString());
			respond(response, "{v:" + results.toString() + "}");

			//			JSONWriter writer = new JSONWriter(response.getWriter());
		}catch (Exception e) {
			respondException(response, e);
		}
	}


	//TODO: warning when unicode characters appear
	private String buildSparqlInsertQuery(String graph, Repository model, RdfSchema schema) {

		StringWriter triples = new StringWriter();
		StringBuilder query = new StringBuilder();


		//we skip the prefixes because they are not necessary for inserting triples into triplestore
		try{
			RepositoryConnection con = model.getConnection();
			try{
				RDFWriter w = Rio.createWriter(RDFFormat.TURTLE, triples);
				con.export(w);
			}finally{
				con.close();
			}
		}catch(RepositoryException ex){
			throw new RuntimeException(ex);
		}catch(RDFHandlerException ex){
			throw new RuntimeException(ex);
		}

		query.append(VIRTUOSO_INSERT_SPARQL_TEMPLATE
				.replace("[[GRAPH]]", graph)
				.replace("[[DATA_TRIPLES]]", triples.getBuffer().toString()));

		return query.toString();

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


	private static final String VIRTUOSO_INSERT_SPARQL_TEMPLATE = "INSERT DATA IN <[[GRAPH]]> {" +
			"[[DATA_TRIPLES]]" + 
			"}";

	//TODO: figure out what is the maximal number before this gets too slow
	//TODO: it might be smarter to put this into preferences
	private static final long MAX_LIMIT_TRIPLES = 1000; 


}
