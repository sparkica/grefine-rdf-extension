function RdfUploadTriplesDialog(onDone) {
	this._onDone = onDone;
	this._params = {};
	var self = this;
	this._dialog = $(DOM.loadHTML("rdf-extension", "scripts/dialogs/rdf-upload-triples.html"));
	this._elmts = DOM.bind(this._dialog);
	//var dismissBusy = DialogSystem.showBusy();

	console.log("initiating...");

	this._elmts.okButton.click(function() {
		console.log("ALL OK");
		self._params.graph="http://my2example.com";
		//self._params.endpoint = "http://localhost:8890/sparql";
		self._params.endpoint = "http://demo.lod2.eu/virtuoso/sparql";
		
		//TODO: make sure schema exists
		
		if(!theProject.overlayModels.rdfSchema)
		{
			alert("No schema defined!");
			DialogSystem.dismissUntil(self._level - 1);

		}
		
		self._onDone(self._params);
		DialogSystem.dismissUntil(self._level - 1);
			
			
	});

	this._elmts.cancelButton.click(function() {
		DialogSystem.dismissUntil(self._level - 1);
	});

	this._level = DialogSystem.showDialog(this._dialog);
	//dismissBusy();
}


RdfUploadTriplesDialog.prototype.uploadData = function() {
	var self = this;
}