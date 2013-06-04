package org.deri.grefine.rdf.commands;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.auth.params.AuthPNames;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.AuthPolicy;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.deri.grefine.rdf.Node;
import org.deri.grefine.rdf.RdfSchema;
import org.deri.grefine.rdf.exporters.RdfExporter.RdfRowVisitor;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
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
            String query = buildSparqlInsertQuery(graph, model, schema);			

            
            //todo: get credentials from form?
            String username = "lodrefine";
            String pwd = "lodrefine";
            
            //todo: using proxy?

            //if I cannot put this into post?
            Map<String, String> virt_params = new LinkedHashMap<String, String>();
            virt_params.put("query", query);

            //parse host and port from endpoint
            URL endpoint_url = new URL(endpoint);
            
            //the POST request using Digest authentication
            DefaultHttpClient client = new DefaultHttpClient();
            client.getCredentialsProvider().setCredentials(
                    new AuthScope(endpoint_url.getHost(),endpoint_url.getPort()),
                    new UsernamePasswordCredentials(username, pwd)
                    );
            client.getParams().setParameter(AuthPNames.PROXY_AUTH_PREF, AuthPolicy.DIGEST);
            
            ResponseHandler<String> rsp = new BasicResponseHandler();
           
           //todo: put query in body of request
           HttpPost httpPost = new HttpPost(endpoint);
           
           List <NameValuePair> nvps = new ArrayList <NameValuePair>();
           nvps.add(new BasicNameValuePair("query", query));
           httpPost.setEntity(new UrlEncodedFormEntity(nvps));
           HttpResponse response2 = client.execute(httpPost);

           System.out.println("STATUS: " + response2.getStatusLine());
           
   
            //TODO: add to schema - option to select which nodes to export and upload
            //for(;results.hasNext();) {
            //	QuerySolution sol = results.next();
            //	
            //	System.out.println(sol.toString());
            //} 

            String results = "just some results for testing";
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

    
    public static String buildQueryFromParameters(Map<String, String> parameters) {
        StringBuilder sb = new StringBuilder();
        
        try {
                for (Map.Entry<String, String> entry: parameters.entrySet()) {
                        String key = entry.getKey();
                        String value = URLEncoder.encode(entry.getValue(),"UTF-8");                                                                             
                        sb.append(key).append("=").append(value).append("&");
                }       
        }
        catch(UnsupportedEncodingException  e)
        {
                e.printStackTrace();
        }

        //remove last &
        return sb.toString().substring(0, sb.length()-1);

}

    private static final String VIRTUOSO_INSERT_SPARQL_TEMPLATE = "INSERT DATA IN <[[GRAPH]]> {" +
            "[[DATA_TRIPLES]]" + 
            "}";

    //TODO: figure out what is the maximal number before this gets too slow
    //TODO: it might be smarter to put this into preferences
    private static final long MAX_LIMIT_TRIPLES = 1000; 


}
