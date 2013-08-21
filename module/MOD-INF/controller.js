var logger = Packages.org.slf4j.LoggerFactory.getLogger("rdf-extension"),
	refineServlet = Packages.com.google.refine.RefineServlet,
	File = Packages.java.io.File,
	update = Packages.org.deri.grefine.rdf.update;

importPackage(org.deri.grefine.rdf.commands);
importPackage(org.deri.grefine.reconcile.commands);
importPackage(org.deri.grefine.rdf.update);
importPackage(org.deri.grefine.rdf.update.commands);
var GRefineServiceManager = Packages.org.deri.grefine.reconcile.GRefineServiceManager;

var rdfReconcileExtension = {
	urlRegex : /^services\/[-.a-zA-Z0-9_]+((\/(pre)?view((\/template)?))|((\/suggest\/(type|property|entity)(\/(pre)?view)?)?$))/g,
	suggestUrlRegex: /^services\/[-.a-zA-Z0-9_]+\/suggest\/(type|property|entity)$/g,
	flyoutUrlRegex: /^services\/[-.a-zA-Z0-9_]+\/suggest\/(type|property|entity)\/preview/g,
	previewUrlRegex:/^services\/[-.a-zA-Z0-9_]+\/preview/g,
	templatePreviewUrlRegex:/^services\/[-.a-zA-Z0-9_]+\/preview\/template/g,
	viewUrlRegex:/^services\/[-.a-zA-Z0-9_]+\/view/g,
	isKnownRequestUrl : function(path){
		 return path.match(this.urlRegex);
	},
	
	getCommand: function(path, request){
		
		var method = request.getMethod();
		var parameters = request.getParameterMap().keySet();
		if(method == 'GET'){
			if(path.match(this.flyoutUrlRegex)){
				//it is a preview request
				if(path.indexOf('/type/')!=-1){
					return 'flyout-type';
				}
				if(path.indexOf('/property/')!=-1){
					return 'flyout-property';
				}
				if(path.indexOf('/entity/')!=-1){
					return 'flyout-entity';
				}
				return 'unknown';
			}
			if(path.match(this.suggestUrlRegex)){
				//it is a suggest request
				return 'suggest-' + path.substring(path.lastIndexOf('/')+1);
			}
			if(path.match(this.viewUrlRegex)){
				//it is a view resource request
				return 'view-resource';
			}
			if(path.match(this.templatePreviewUrlRegex)){
				return 'preview-resource-template';
			}
			if(path.match(this.previewUrlRegex)){
				//it is a preview resource request
				return 'preview-resource';
			}
			//metadata request is a GET request with 'callback' parameter
			if(parameters.contains('callback')){
				return 'metadata';
			}
		}else if(method == 'POST'){
			//multi reconcile request is a POST request that has 'queries' parameter (not necessarily alone)
			if(parameters.contains('queries')){
				return 'multi-reconcile';
			}
		}
		return 'unknown';
	},
	
	getServiceName: function(path){
		var urlSuffix = path.substring(9);
		var firstSlashinSuffixIndex = urlSuffix.indexOf('/');
		if(firstSlashinSuffixIndex == -1){
			return urlSuffix;
		}else{
			return urlSuffix.substring(0,firstSlashinSuffixIndex);
		}
	}
};
/*
 * Function invoked to initialize the extension.
 */
var RS = Packages.com.google.refine.RefineServlet;


function init() {
	
	logger.info("Initializing rdf extension");
	var cacheFolder = new refineServlet().getCacheDir("rdf-extension");
    logger.info("Initializing endpoint manager");
	var endpointManager = new update.EndpointServiceManager(new File(cacheFolder + "/registered-update-endpoints.json"));
	
	RS.registerClassMapping(
	        "org.deri.grefine.operations.SaveRdfSchemaOperation$RdfSchemaChange",
	        "org.deri.grefine.rdf.operations.SaveRdfSchemaOperation$RdfSchemaChange");
	
	RS.cacheClass(Packages.org.deri.grefine.rdf.operations.SaveRdfSchemaOperation$RdfSchemaChange);
	/*
     * Context Initialization. This is mainly to allow testability. a simple attempt to mimic dependency injection
     */
    var initializer = new Packages.org.deri.grefine.rdf.app.InitilizationCommand();
    RS.registerCommand(module, "initialize", initializer);
    var ctxt = new Packages.org.deri.grefine.rdf.app.ApplicationContext();
    initializer.initRdfExportApplicationContext(ctxt);
    
    /*
     *  Attach an rdf schema to each project.
     */
    Packages.com.google.refine.model.Project.registerOverlayModel(
        "rdfSchema",
        Packages.org.deri.grefine.rdf.RdfSchema);
    
    /*
     *  Operations
     */
    Packages.com.google.refine.operations.OperationRegistry.registerOperation(
        module, "save-rdf-schema", Packages.org.deri.grefine.rdf.operations.SaveRdfSchemaOperation);
    
    /*
     *  Exporters
     */
    var ExporterRegistry = Packages.com.google.refine.exporters.ExporterRegistry;
    var RdfExporter = Packages.org.deri.grefine.rdf.exporters.RdfExporter;
    
    ExporterRegistry.registerExporter("rdf", new RdfExporter(ctxt,org.openrdf.rio.RDFFormat.RDFXML));
    ExporterRegistry.registerExporter("Turtle", new RdfExporter(ctxt,org.openrdf.rio.RDFFormat.TURTLE));
    
    /*
     *  GREL Functions and Binders
     */
    Packages.com.google.refine.grel.ControlFunctionRegistry.registerFunction(
        "urlify", new Packages.org.deri.grefine.rdf.expr.functions.strings.Urlify());
        
    Packages.com.google.refine.expr.ExpressionUtils.registerBinder(
        new Packages.org.deri.grefine.rdf.expr.RdfBinder(ctxt));
        
    /*
     *  Commands
     */
    RS.registerCommand(module, "save-rdf-schema", new SaveRdfSchemaCommand(ctxt));
    RS.registerCommand(module, "preview-rdf", new PreviewRdfCommand());
    RS.registerCommand(module, "save-baseURI", new SaveBaseURICommand(ctxt));
    RS.registerCommand(module, "preview-rdf-expression", new PreviewRdfValueExpressionCommand());
    //Vocabs commands
    RS.registerCommand(module, "save-prefixes", new SavePrefixesCommand(ctxt));
    RS.registerCommand(module, "get-default-prefixes", new GetDefaultPrefixesCommand(ctxt));
    RS.registerCommand(module, "add-prefix", new AddPrefixCommand(ctxt));
    RS.registerCommand(module, "remove-prefix", new RemovePrefixCommand(ctxt));
    RS.registerCommand(module, "refresh-prefix", new RefreshPrefixCommand(ctxt));
    RS.registerCommand(module, "suggest-term", new SuggestTermCommand(ctxt));
    RS.registerCommand(module, "get-prefix-cc-uri", new SuggestPrefixUriCommand(ctxt));
    RS.registerCommand(module, "upload-file-add-prefix", new AddPrefixFromFileCommand(ctxt));
    //Reconcile commands
	RS.registerCommand(module, "addService", new AddServiceCommand());
	RS.registerCommand(module, "uploadFileAndAddService", new UploadFileAndAddServiceCommand());
	RS.registerCommand(module, "sindiceGuessType", new SindiceGuessTypeCommand());
	RS.registerCommand(module, "addSindiceService", new AddSindiceService());
	RS.registerCommand(module, "addStanbolService", new AddStanbolServiceCommand());
	RS.registerCommand(module, "initializeServices", new InitializeServicesCommand());
	RS.registerCommand(module, "load-language", new LoadLanguageCommand());
	
	//Upload triples command
	RS.registerCommand(module, "upload-triples", new UploadRdfCommand(endpointManager));
	
	//RefineServlet.registerCommand(module, "sindiceReconcile", new SindiceReconcileCommand());
	//this is just to initialize ServiceRegistry
	RS.registerCommand(module, "reconcile-initialize", new InitializationCommand());
       
    /*
     *  Client-side Resources
     */
    var ClientSideResourceManager = Packages.com.google.refine.ClientSideResourceManager;
    
    // Script files to inject into /project page
    ClientSideResourceManager.addPaths(
        "project/scripts",
        module,
        [
            "scripts/rdf-data-table-view.js",
            "scripts/dialogs/rdf-upload-triples.js",
            "scripts/menu-bar-extensions.js",
            "scripts/rdf-schema-alignment.js",
            "scripts/rdf-schema-alignment-ui-node.js",
            "scripts/rdf-schema-alignment-ui-link.js",
            "scripts/suggestterm.suggest.js",
            "scripts/rdf-schema-manage-vocabs-widget.js",
            "scripts/rdf-schema-vocabulary-manager.js",
            "scripts/rdf-schema-new-prefix-widget.js",
            "scripts/externals/jquery.form.js",
            "scripts/sindice/sindice-dialog.js",
            "scripts/common.js"
		]
    );
    
    // Style files to inject into /project page
    ClientSideResourceManager.addPaths(
        "project/styles",
        module,
        [
            "styles/rdf-schema-alignment-dialog.css",
			"styles/rdf-reconcile-service.css",
            "styles/sindice/recon-dialog.css",
            "styles/dialogs.css"
        ]
    );
    
}

function process(path, request, response) {
    // Analyze path and handle this request yourself.
	var loggerFactory = Packages.org.slf4j.LoggerFactory;
	var logger = loggerFactory.getLogger("rdf_extension");
    var method = request.getMethod();
    
    logger.info('receiving request for ' + path);
    if(rdfReconcileExtension.isKnownRequestUrl(path)){
    	var command = rdfReconcileExtension.getCommand(path, request);
    	logger.info('command is ' + command);
    	var serviceName = rdfReconcileExtension.getServiceName(path);
    	logger.info('command is ' + command + ', while service name is ' + serviceName);
    	if(command && command !== 'unknown'){
    		var jsonResponse;
    		if(command==='metadata'){
    			jsonResponse = GRefineServiceManager.singleton.metadata(serviceName,request);
    		}else if(command==='multi-reconcile'){
    			jsonResponse = GRefineServiceManager.singleton.multiReconcile(serviceName,request);
    		}else if (command==='suggest-type'){
    			jsonResponse = GRefineServiceManager.singleton.suggestType(serviceName,request);
    		}else if (command==='flyout-type'){
    			jsonResponse = GRefineServiceManager.singleton.previewType(serviceName,request);
    		}else if (command==='suggest-property'){
    			jsonResponse = GRefineServiceManager.singleton.suggestProperty(serviceName,request);
    		}else if (command==='flyout-property'){
    			jsonResponse = GRefineServiceManager.singleton.previewProperty(serviceName,request);
    		}else if (command==='suggest-entity'){
    			jsonResponse = GRefineServiceManager.singleton.suggestEntity(serviceName,request);
    		}else if (command==='flyout-entity'){
    			jsonResponse = GRefineServiceManager.singleton.previewEntity(serviceName,request);
    		}else if (command==='preview-resource-template'){
    			var htmlResponse = GRefineServiceManager.singleton.getHtmlOfResourcePreviewTemplate(serviceName,request);
    			if(htmlResponse){
    				butterfly.sendString(request, response, htmlResponse ,"UTF-8", "text/html");
    			}else{
    				butterfly.sendError(request, response, 404, "unknown service");
    			}
    			return;
    		}else if (command==='view-resource'){
    			var id = request.getParameter('id');
    			butterfly.redirect(request,response,id);
    			return;
    		}else if (command ==='preview-resource'){
    			logger.info("id is " + request.getParameter("id"));
    			var htmlResponse = GRefineServiceManager.singleton.previewResource(serviceName,request);
    			if(htmlResponse){
    				butterfly.sendString(request, response, htmlResponse ,"UTF-8", "text/html");
    			}else{
    				butterfly.sendError(request, response, 404, "unknown service");
    			}
    			return;
    		}
    		
    		if(jsonResponse){
    			logger.info(jsonResponse);
    			butterfly.sendString(request, response, jsonResponse ,"UTF-8", "text/javascript");
    			return;
    		}else{
    			butterfly.sendError(request, response, 404, "unknown service");
    		}
    	}
    	//else it is an unknown command... do nothing
    }
    
    if (path == "/" || path == "") {
    	butterfly.redirect(request, response, "index.html");
    }
}