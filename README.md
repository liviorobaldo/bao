This repository contains the source code and resources associated with the paper *"The Boolean Algebra Ontology: Representing and Reasoning with Truth and Falsity in the Semantic Web"*.

The automated reasoner is implemented in the Java class `InferAndValidateThroughSHACL.java`, which can be compiled by running `compile.bat` on Windows or by executing the corresponding Java compilation commands provided in this file on other operating systems.

To execute one of the examples contained in the folder `BAO/Examples`, run the file `runExample.bat`. The specific example to execute can be configured directly inside this batch file. The automated reasoner outputs the inferred triples to the file `BAO/inferredTriples.ttl`. It then performs SHACL validation over the inferred knowledge graph and prints the validation results to the standard output.

The RDF resources, SHACL shapes, and SHACL-SPARQL rules of the Boolean Algebra Ontology are defined in the file `BAO/BaoTBox.ttl`. This file is loaded by the automated reasoner to perform inference and validate the resulting knowledge graph. The file `BAO/UnoTBox.ttl` contains the RDF resources and SHACL-SPARQL rules implementing the Unique Name Ontology, described in the final section of the paper, immediately before the Conclusions.
