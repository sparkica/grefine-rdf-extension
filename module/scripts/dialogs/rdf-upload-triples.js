function RdfUploadTriplesDialog(onDone) {
	this._onDone = onDone;
	this._params = {};
	var self = this;
	this._dialog = $(DOM.loadHTML("rdf-extension", "scripts/dialogs/rdf-upload-triples.html"));
	this._elmts = DOM.bind(this._dialog);
	//var dismissBusy = DialogSystem.showBusy();

	console.log("initiating...");

	this._elmts.okButton.click(function() {

		//TODO: validate input fields
		self._params.endpoint_type = self._elmts.endpointTypes.children(":selected").val();
		
		//TODO: verify it is really an endpoint or at least URL
		//TODO:graph should be URI
		self._params.endpoint = self._elmts.endpoint;
		self._params.graph = self._elmts.defaultGraph;
		
		//get credentials
		//if checkbox checked, get credentials from config file at the load time
		//else 
		
		//how to store credentials? encode them and then decode them?
		
		if ($('#config-creds').is(':checked')) {
			alert("Username and password are provided at the installation time. " +
					"They should be looked up in config file.")
		}
		
		self._params.user = self._elmts.userName;
		self._params.user = self._elmts.userPwd;
		
		if(!theProject.overlayModels.rdfSchema)
		{
			alert("No schema defined! Please define schema first.");
			DialogSystem.dismissUntil(self._level - 1);
		}
		
		self._onDone(self._params);
		DialogSystem.dismissUntil(self._level - 1);
			
			
	});

	this._elmts.cancelButton.click(function() {
		DialogSystem.dismissUntil(self._level - 1);
	});
	
	this._elmts.endpointsList.change(function () {
		self._elmts.endpoint.val = self._elmts.endpointsList.children(":selected").val()
	});

	this._level = DialogSystem.showDialog(this._dialog);
	//dismissBusy();
}


RdfUploadTriplesDialog.prototype.uploadData = function() {
	var self = this;
}