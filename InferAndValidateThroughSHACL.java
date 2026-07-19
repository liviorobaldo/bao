import java.util.*;
import java.io.*;
import java.util.stream.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;
import org.apache.jena.graph.*;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.rulesys.RDFSRuleReasonerFactory;
import org.apache.jena.vocabulary.*;
import org.apache.jena.update.*;
import org.topbraid.shacl.rules.*;
import org.topbraid.shacl.validation.*;

public class InferAndValidateThroughSHACL 
{
    private static String TBoxBAOFile = "./BAO/BaoTBox.ttl";
    private static String TBoxUNOFile = "./BAO/UnoTBox.ttl";
    private static String ABoxFile = "./BAO/Examples/Example7.ttl";
    private static File inferredTriplesFile = new File("./BAO/inferredTriples.ttl");
    
    public static void main(String[] args) throws Exception 
    {
        if(args.length>0)ABoxFile=args[0];
		
        Model model = ModelFactory.createDefaultModel();

        /************************************************************************************************************************
        // LOADING THE TWO TBOXES INTO ONE
        /************************************************************************************************************************/
        Model TBox = RDFDataMgr.loadModel(TBoxBAOFile);
        Model TBoxUNO = RDFDataMgr.loadModel(TBoxUNOFile);
        TBox.add(TBoxUNO);
        
        model.add(TBox);
        List<Statement> tboxStatements = TBox.listStatements().toList();

        /************************************************************************************************************************
            LOADING ABOX
        /************************************************************************************************************************/
        
        Model ABox = RDFDataMgr.loadModel(ABoxFile);
            // The following lines are required by the UNO ontology, which is built on top of RDFS.
            // The ontology relies on RDFS entailments, in particular the fact that every resource
            // occurring in the graph is an instance of rdfs:Resource. Therefore, the ABox is loaded
            // as an RDFS inference model.
            //
            // However, enabling the full RDFS reasoner (ReasonerVocabulary.RDFS_FULL) is generally
            // not advisable from a computational point of view, as it populates the graph with a
            // large number of inferred triples, including explicit assertions that every resource
            // is an instance of rdfs:Resource. This can significantly increase the graph size and
            // substantially degrade reasoning performance.
        Reasoner rdfsReasoner = RDFSRuleReasonerFactory.theInstance().create(null);
        rdfsReasoner.setParameter(ReasonerVocabulary.PROPsetRDFSLevel, ReasonerVocabulary.RDFS_FULL);
        ABox = ModelFactory.createInfModel(rdfsReasoner, ABox);
        model.add(ABox);
        
        /************************************************************************************************************************
            INFERENCE (RULE SATURATION)
        /************************************************************************************************************************/
        long lastSize = 0;
        while(model.size() > lastSize) 
        {
            lastSize = model.size();
            model = RuleUtil.executeRules(model, model, null, null).add(model);
            //print(model, TBox, inferredTriplesFile);
        }
        
        removeRedundantTriple(model);

            //Printing inferred triples
        print(model, TBox, inferredTriplesFile);


        /************************************************************************************************************************
            VALIDATION (SHACL)
        /************************************************************************************************************************/
        Model report = ValidationUtil.validateModel(removeRDFstarNotation(model), model, false).getModel();
        report.write(System.out, "TURTLE");
    }

        //This method executes the SPARQL DELETE-WHERE queries to remove the redundant triples from the graph after the rules 
        //for processing disjunctions have been applied.
    private static void removeRedundantTriple(Model model)
    {
        String deleteQuery =
            model.getNsPrefixMap().entrySet().stream()
                .map(e->"PREFIX "+e.getKey()+": <"+e.getValue()+">\n")
                .collect(Collectors.joining())+"\n"+
            "DELETE{?d1 ?p ?o}WHERE{?d1 bao:entails-disjunction ?d2. ?d1 ?p ?o}";
        UpdateAction.parseExecute(deleteQuery, model);
        
        deleteQuery =
            model.getNsPrefixMap().entrySet().stream()
                .map(e->"PREFIX "+e.getKey()+": <"+e.getValue()+">\n")
                .collect(Collectors.joining())+"\n"+
        "DELETE{?d a bao:Disjunction}"+
        "WHERE{?d a bao:Disjunction. FILTER NOT EXISTS{?d bao:has-disjunct ?x}}";
        UpdateAction.parseExecute(deleteQuery, model);
        
        deleteQuery =
            model.getNsPrefixMap().entrySet().stream()
                .map(e->"PREFIX "+e.getKey()+": <"+e.getValue()+">\n")
                .collect(Collectors.joining())+"\n"+
        "DELETE{?r a bao:True}WHERE{?r a bao:True}";
        UpdateAction.parseExecute(deleteQuery, model);
    }
    
        //UTILITY TO PRINT THE MODEL
    private static void print(Model model, Model TBox, File inferredTriplesFile)throws Exception
    {
            //We only output the inferred statements.
        List<Statement> finalStatements = model.listStatements().toList();
            
            //we remove these prefixes and the triples in the TBox.
        model.removeNsPrefix("owl");
        model.removeNsPrefix("tosh");
        model.removeNsPrefix("dash");
        model.removeNsPrefix("graphql");
        model.removeNsPrefix("swa");
        model.removeNsPrefix("xsd");
        finalStatements.removeAll(TBox.listStatements().toList());
        
            //In the inferred statements it adds a lot of triples inferred via RDFS schema from the Time Ontology.
            //We are not interested in these, so we remove them, we only keep the triples with the properties we are
            //interested in, namely the ones in Figure 1 in the paper.
        ArrayList<Statement> toRemove = new ArrayList<Statement>();

        for(Statement finalStatement:finalStatements)
        {
            Resource subject = finalStatement.getSubject();
            Property predicate = finalStatement.getPredicate();
            RDFNode object = finalStatement.getObject();
            if(predicate.getURI().compareToIgnoreCase("http://www.w3.org/ns/shacl#namespace")==0)toRemove.add(finalStatement);
            else if(predicate.getURI().compareToIgnoreCase("http://www.w3.org/ns/shacl#construct")==0)toRemove.add(finalStatement);
            else if(predicate.getURI().compareToIgnoreCase("http://www.w3.org/ns/shacl#prefix")==0)toRemove.add(finalStatement);
            else if(predicate.getURI().compareToIgnoreCase("http://www.w3.org/ns/shacl#prefixes")==0)toRemove.add(finalStatement);
            else if(predicate.getURI().compareToIgnoreCase("http://www.w3.org/ns/shacl#declare")==0)toRemove.add(finalStatement);
            else if(predicate.getURI().compareToIgnoreCase("http://www.w3.org/ns/shacl#rule")==0)toRemove.add(finalStatement);
            else if(predicate.getURI().compareToIgnoreCase("http://www.w3.org/ns/shacl#targetNode")==0)toRemove.add(finalStatement);
            else if(object.toString().compareToIgnoreCase("http://www.w3.org/ns/shacl#NodeShape")==0)toRemove.add(finalStatement);
            else if(object.toString().compareToIgnoreCase("http://www.w3.org/ns/shacl#SPARQLRule")==0)toRemove.add(finalStatement);
            else if(object.toString().compareToIgnoreCase("https://w3id.org/unique-name-ontology#UniqueName")==0)toRemove.add(finalStatement);
                //The next lines removes all RDFS inferred statements.
            else if(object.toString().compareToIgnoreCase("http://www.w3.org/2000/01/rdf-schema#Resource")==0)toRemove.add(finalStatement);
            else if(object.toString().compareToIgnoreCase("http://www.w3.org/2000/01/rdf-schema#Class")==0)toRemove.add(finalStatement);
            else if(object.toString().compareToIgnoreCase("http://www.w3.org/2000/01/rdf-schema#Datatype")==0)toRemove.add(finalStatement);
            else if(predicate.getURI().compareToIgnoreCase("http://www.w3.org/2000/01/rdf-schema#subClassOf")==0)toRemove.add(finalStatement);
            else if(predicate.getURI().compareToIgnoreCase("http://www.w3.org/2000/01/rdf-schema#subPropertyOf")==0)toRemove.add(finalStatement);
            else if(predicate.getURI().compareToIgnoreCase("http://www.w3.org/2000/01/rdf-schema#domain")==0)toRemove.add(finalStatement);
            else if(predicate.getURI().compareToIgnoreCase("http://www.w3.org/2000/01/rdf-schema#range")==0)toRemove.add(finalStatement);
            else if(object.toString().compareToIgnoreCase("http://www.w3.org/1999/02/22-rdf-syntax-ns#Property")==0)toRemove.add(finalStatement);
            else if(object.toString().compareToIgnoreCase("http://www.w3.org/1999/02/22-rdf-syntax-ns#List")==0)toRemove.add(finalStatement);
        }
        finalStatements.removeAll(toRemove);
        
        Model outputModel = ModelFactory.createDefaultModel();
        outputModel.setNsPrefixes(model.getNsPrefixMap());
        outputModel.add(finalStatements);
        FileOutputStream outputStream = new FileOutputStream(inferredTriplesFile);
        RDFDataMgr.write(outputStream, outputModel, RDFFormat.TURTLE_BLOCKS);
        outputStream.close();        
    }
    
    
        //Utility to replace every "<<:a :b :c>>" with "[a rdf:Statement; rdf:subject :a; rdf:predicate :b; rdf:object :c]". 
        //The former notation is supported by Apache Jena, which incorporates RDF-star, but not by the original SHACL 2017 Recommendation.
        //ValidationUtil.validateModel is defined with respect to the SHACL 2017 Recommendation. 
    public static Model removeRDFstarNotation(Model sourceModel) 
    {
        Model expandedModel = ModelFactory.createDefaultModel();
        Map<String, Resource> reificationCache = new HashMap<>();
        expandedModel.add(sourceModel);

            //Extract a list of statements containing nested triples to parse
        List<Statement> starStatements = new ArrayList<>();
        sourceModel.listStatements().forEachRemaining(stmt -> 
        {
            Node sNode = stmt.getSubject().asNode();
            Node oNode = stmt.getObject().asNode();
            if(((sNode!=null)&&(sNode.isNodeTriple()))||((oNode!=null)&&(oNode.isNodeTriple())))starStatements.add(stmt);
        });

            //We can have <<...>> nested inside other <<...>>s. So, we use an iterative processing queue to handle arbitrary nesting levels
        for(Statement stmt:starStatements) 
        {
            expandedModel.remove(stmt);
            RDFNode[] targets = {stmt.getSubject(),stmt.getObject()};
            Resource finalSubject = null;
            RDFNode finalObject = null;

            for(int i=0;i<2;i++)
            {
                RDFNode currentRoot = targets[i];
                if(currentRoot==null)continue;

                Node rootNode = currentRoot.asNode();
                if(!rootNode.isNodeTriple()) 
                {
                    if(i==0)finalSubject=currentRoot.asResource();
                    else finalObject=currentRoot;
                    continue;
                }

                Deque<Node> nodesToExpand = new ArrayDeque<>();
                Deque<Node> processingStack = new ArrayDeque<>();
                processingStack.push(rootNode);

                while (!processingStack.isEmpty()) 
                {
                    Node current = processingStack.pop();
                    nodesToExpand.push(current);
                    Triple t = current.getTriple();
                    if (t.getSubject().isNodeTriple()) processingStack.push(t.getSubject());
                    if (t.getObject().isNodeTriple()) processingStack.push(t.getObject());
                }

                while(!nodesToExpand.isEmpty()) 
                {
                    Node nodeToReify = nodesToExpand.pop();
                    String nodeKey = nodeToReify.toString();
                    if (!reificationCache.containsKey(nodeKey))
                    {
                        Resource reified = expandedModel.createResource("urn:reified:" + Math.abs(nodeKey.hashCode()));
                        Triple t = nodeToReify.getTriple();
                        Node sNodeSub = t.getSubject();
                        RDFNode sNodeFinal = sNodeSub.isNodeTriple() ? reificationCache.get(sNodeSub.toString()) : expandedModel.asRDFNode(sNodeSub);
                        Node pNodeSub = t.getPredicate();
                        RDFNode pNodeFinal = expandedModel.asRDFNode(pNodeSub);
                        Node oNodeSub = t.getObject();
                        RDFNode oNodeFinal = oNodeSub.isNodeTriple() ? reificationCache.get(oNodeSub.toString()) : expandedModel.asRDFNode(oNodeSub);
                        reified.addProperty(RDF.type, RDF.Statement);
                        reified.addProperty(RDF.subject, sNodeFinal);
                        reified.addProperty(RDF.predicate, pNodeFinal);
                        reified.addProperty(RDF.object, oNodeFinal);
                        reificationCache.put(nodeKey, reified);
                    }
                }

                if(i==0)finalSubject=reificationCache.get(rootNode.toString());
                else finalObject=reificationCache.get(rootNode.toString());
            }

            expandedModel.add(expandedModel.createStatement(finalSubject, stmt.getPredicate(), finalObject));
        }

        return expandedModel;
    }
}
