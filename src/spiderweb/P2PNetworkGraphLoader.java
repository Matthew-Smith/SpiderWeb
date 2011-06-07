package spiderweb;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.LinkedList;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

import edu.uci.ics.jung.graph.util.Pair;

public class P2PNetworkGraphLoader {
	private LinkedList<LogEvent> logList;
	private P2PNetworkGraph hiddenGraph;
	private P2PNetworkGraph visibleGraph;
	private List<LoadingListener> loadingListeners;

	//[start] Constructor
	public P2PNetworkGraphLoader() {
		logList = new LinkedList<LogEvent>();
		hiddenGraph = new P2PNetworkGraph();
		visibleGraph = new P2PNetworkGraph();
		loadingListeners = new LinkedList<LoadingListener>();
	}
	//[end] Constructor

	//[start] Loading Method
	/**
	 * @return <code>true</code> if file loaded successfully
	 */
	public boolean doLoad() {
		String[] acceptedExtensions = { "xml","txt" };
		File file = chooseLoadFile(".xml and .txt Files", acceptedExtensions);
		if(file != null) {
			if(file.getAbsolutePath().endsWith(".txt")) {
				try {
					loadingStarted(1,"Log Files");
					LogEventListBuilder logBuilder = new LogEventListBuilder();
					for(LoadingListener l : loadingListeners) {
						logBuilder.addLoadingListener(l);
					}
					logList = logBuilder.createLinkedList(new BufferedReader(new FileReader(file)));
					hiddenGraph = logBuilder.getHiddenGraph(); //load hidden graph but keep visible graph empty
					loadingComplete();
					return true;
				} catch(Exception e) {
					JOptionPane.showMessageDialog(null, e.getMessage(), "Failure", JOptionPane.ERROR_MESSAGE);
				}
			}
			else if(file.getAbsolutePath().endsWith(".xml")) {
				try {
					SAXBuilder builder = new SAXBuilder();
					final Document networkDoc = builder.build(file);
					graphBuilder(networkDoc);
					//if(logList.isEmpty()) {
						//visibleGraph = hiddenGraph;
					//}
					return true;
				} catch(Exception e) {
					JOptionPane.showMessageDialog(null, e.getMessage(), "Failure", JOptionPane.ERROR_MESSAGE);
				}
			}
		}
		return false;
	}
	//[end] Loading Method

	//[start] Listener Methods
	public void addLoadingListener(LoadingListener listener) {
		loadingListeners.add(listener);
	}

	private void loadingStarted(int numberLines, String whatIsLoading) {
		for(LoadingListener l : loadingListeners) {
			l.loadingStarted(numberLines, whatIsLoading);
		}
	}

	private void loadingProgress(int progress) {
		for(LoadingListener l : loadingListeners) {
			l.loadingProgress(progress);
		}
	}

	private void loadingChanged(int numberLines, String whatIsLoading) {
		for(LoadingListener l : loadingListeners) {
			l.loadingChanged(numberLines, whatIsLoading);
		}
	}

	private void loadingComplete() {
		for(LoadingListener l : loadingListeners) {
			l.loadingComplete();
		}
	}
	//[end] Listener Methods

	//[start] Graph Builder
	private void graphBuilder(Document networkDoc) {
		if(networkDoc.getRootElement().getName().equals("network")) {
			int edgeCounter=0;
			P2PNetworkGraph startGraph = new P2PNetworkGraph();
			Element networkElem = networkDoc.getRootElement();
			int counter = 0;
			//[start] Create Graph
			Element graphElem = networkElem.getChild("graph");
			if(graphElem != null) {
				//[start] Add Vertices to graph
				Element nodeMap = graphElem.getChild("nodemap");
				loadingStarted(nodeMap.getChildren().size(), "Vertices");

				for(Object o : nodeMap.getChildren()) {
					Element elem = (Element)o;
					String type = elem.getAttribute("type").getValue();
					
					if(type.equals("PeerVertex")) {
						int key = Integer.parseInt(elem.getChild("key").getText());
						hiddenGraph.addVertex(new PeerVertex(key));
						startGraph.addVertex(new PeerVertex(key));
					}
					else if(type.equals("DocumentVertex")) {
						int key = Integer.parseInt(elem.getChild("key").getText());
						hiddenGraph.addVertex(new DocumentVertex(key));
						startGraph.addVertex(new DocumentVertex(key));
					}
					loadingProgress(++counter);
				}
				//[end] Add Vertices to graph

				//[start] Add Edges to graph
				Element edgeMap = graphElem.getChild("edgemap");
				loadingChanged(edgeMap.getChildren().size(), "Edges");
				counter=0;
				
				for(Object o : edgeMap.getChildren()) {
					Element elem = (Element)o;
					String type = elem.getAttribute("type").getValue();

					if(type.equals("PeerToPeer")) { //Peer to Peer
						int v1Key = Integer.parseInt(elem.getChild("v1").getText());
						int v2Key = Integer.parseInt(elem.getChild("v2").getText());
						P2PVertex peer1 = hiddenGraph.getVertexInGraph(new PeerVertex(v1Key));
						P2PVertex peer2 = hiddenGraph.getVertexInGraph(new PeerVertex(v2Key));
						Pair<P2PVertex> pair = new Pair<P2PVertex>(peer1, peer2);
						startGraph.addEdge(new P2PConnection(P2PConnection.P2P,edgeCounter),pair);
						hiddenGraph.addEdge(new P2PConnection(P2PConnection.P2P,edgeCounter),pair);
						edgeCounter++;
					}
					else if(type.equals("PeerToDocument")) { //Peer to Document
						int v1Key = Integer.parseInt(elem.getChild("v1").getText());
						int v2Key = Integer.parseInt(elem.getChild("v2").getText())%1000;
						P2PVertex document = hiddenGraph.getVertexInGraph(new DocumentVertex(v2Key));
						P2PVertex peer = hiddenGraph.getVertexInGraph(new PeerVertex(v1Key));
						Pair<P2PVertex> pair = new Pair<P2PVertex>(peer, document);
						startGraph.addEdge(new P2PConnection(P2PConnection.P2DOC,edgeCounter),pair);
						hiddenGraph.addEdge(new P2PConnection(P2PConnection.P2DOC,edgeCounter),pair);
						edgeCounter++;
						
						PeerDocumentVertex pdv = new PeerDocumentVertex(v1Key, v2Key);
						hiddenGraph.addVertex(pdv);
						startGraph.addVertex(pdv);
						startGraph.addEdge(new P2PConnection(P2PConnection.P2PDOC,edgeCounter),peer,pdv);
						hiddenGraph.addEdge(new P2PConnection(P2PConnection.P2PDOC,edgeCounter),peer,pdv);
						edgeCounter++;
						
						startGraph.addEdge(new P2PConnection(P2PConnection.DOC2PDOC,edgeCounter),document,pdv);
						hiddenGraph.addEdge(new P2PConnection(P2PConnection.DOC2PDOC,edgeCounter),document,pdv);
						edgeCounter++;
					}
					loadingProgress(++counter);
				}
				//[end] Add Edges to graph
			}
			//[end] Create Graph

			//[start] Create Logs
			Element logElem = networkElem.getChild("logevents");
			if(logElem != null) {
				loadingChanged(logElem.getChildren().size(), "Events");
				counter=0;
				logList.add(new LogEvent("0:start:0:0"));
				for(Object o : logElem.getChildren()) {

					Element event = (Element)o;
					String type = event.getAttribute("type").getValue();
					if(type.equals("start") || type.equals("end")) {
						continue;
					}
					long timeDifference = Integer.parseInt(event.getChildText("timedifference"));
					int paramOne = Integer.parseInt(event.getChildText("param1"));
					int paramTwo = 0;
					if(LogEvent.typeHasParamTwo(type)) {
						paramTwo = Integer.parseInt(event.getChildText("param2"));
					}
					LogEvent evt = new LogEvent(timeDifference, type, paramOne, paramTwo);
					if(evt.isConstructing()) {
						if (evt.getType().equals("online")){
							hiddenGraph.addVertex(new PeerVertex(evt.getParam(1)));
						} else if(evt.getType().equals("connect")){
							P2PConnection edge = new P2PConnection(P2PConnection.P2P,edgeCounter);
							edgeCounter++;
							P2PVertex from = hiddenGraph.getVertexInGraph(new PeerVertex(evt.getParam(1)));
							P2PVertex to = hiddenGraph.getVertexInGraph(new PeerVertex(evt.getParam(2)));
							hiddenGraph.addEdge(edge, from, to);
						} else if(evt.getType().equals("publish")){
							P2PVertex document = new DocumentVertex(evt.getParam(2));
							P2PVertex peerDocument = new PeerDocumentVertex(evt.getParam(1), evt.getParam(2));
							P2PVertex peer = hiddenGraph.getVertexInGraph(new PeerVertex(evt.getParam(1)));
							
							if(!hiddenGraph.containsVertex(document)) {
								hiddenGraph.addVertex(document);
							}
							else {
								document = hiddenGraph.getVertexInGraph(document);
							}
							hiddenGraph.addVertex(peerDocument);
							
					    	hiddenGraph.addEdge(new P2PConnection(P2PConnection.P2DOC,edgeCounter), peer, document);
					    	edgeCounter++;
					    	hiddenGraph.addEdge(new P2PConnection(P2PConnection.P2PDOC,edgeCounter), peer, peerDocument);
					    	edgeCounter++;
					    	hiddenGraph.addEdge(new P2PConnection(P2PConnection.DOC2PDOC,edgeCounter), document, peerDocument);
					    	edgeCounter++;
						}
					}
					logList.add(evt);
					loadingProgress(++counter);
				}
				logList.add(new LogEvent((logList.get(logList.size()-1).getTime()+100)+":end:0:0"));
			}
			visibleGraph = startGraph;
			//[end] Create Logs
			loadingComplete();
		}
	}
	//[end] Graph Builder

	//[start] Getters
	public LinkedList<LogEvent> getLogList() {
		return logList;
	}
	public P2PNetworkGraph getHiddenP2PNetworkGraph() {
		return hiddenGraph;
	}
	public P2PNetworkGraph getVisibleP2PNetworkGraph() {
		return visibleGraph;
	}
	//[end] Getters

	//[start] Static Methods
	public static File chooseLoadFile(String filterDescription, String[] acceptedExtensions) {
		JFileChooser fileNamer = new JFileChooser();
		fileNamer.setFileFilter(new ExtensionFileFilter(filterDescription, acceptedExtensions));
		int returnVal = fileNamer.showOpenDialog(null);


		if (returnVal == JFileChooser.APPROVE_OPTION) {
			for(String extension : acceptedExtensions) {
				if(fileNamer.getSelectedFile().getAbsolutePath().endsWith(extension)) {

					return fileNamer.getSelectedFile();
				}
			}
			JOptionPane.showMessageDialog(null, "Error: Incorrect extension.", "Error", JOptionPane.ERROR_MESSAGE);

			return null;
		}
		else if (returnVal == JFileChooser.ERROR_OPTION) {
			JOptionPane.showMessageDialog(null, "Error: Could not load file.", "Error", JOptionPane.ERROR_MESSAGE);
		}
		return null;
	}
	//[end] Static Methods
	
}

