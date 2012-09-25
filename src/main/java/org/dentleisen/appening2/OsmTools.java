package org.dentleisen.appening2;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Box AMS:
 * 
 * OL: 52.457,4.712 UR: 52.271,5.042
 * 
 * gzcat ~/Downloads/noord-holland.osm.gz| ./osmosis --read-xml file=- --bb
 * top=52.457 left=4.712 bottom=52.271 right=5.042 --write-xml file=- | gzip >
 * ams.osm.gz
 */
public class OsmTools {
	private static Logger log = Logger.getLogger(OsmTools.class);

	public static class OsmHandler extends DefaultHandler {
		private boolean inNode = false;
		private double nodeLat = 0;
		private double nodeLng = 0;
		private long nodeId = 0;

		private boolean inWay = false;
		private long wayId = 0;
		private Set<Long> wayNodeRefs = new HashSet<Long>();

		private boolean inRel = false;
		private long relId = 0;
		private Set<Long> relNodeRefs = new HashSet<Long>();

		private static class KV {
			public String k;
			public String v;

			@Override
			public String toString() {
				return k + "=" + v;
			}
		}

		private List<KV> tags = new ArrayList<KV>();

		private Map<Long, Place> places = new HashMap<Long, Place>();

		public Map<Long, Place> getPlaces() {
			return places;
		}

		@Override
		public void startElement(String namespaceURI, String localName,
				String qName, Attributes atts) throws SAXException {
			if (localName.equals("node")) {
				inNode = true;
				for (int i = 0; i < atts.getLength(); i++) {
					String aName = atts.getLocalName(i);
					String aValue = atts.getValue(i);

					if (aName.equals("id")) {
						nodeId = Long.parseLong(aValue);
					}
					if (aName.equals("lat")) {
						nodeLat = Double.parseDouble(aValue);
					}
					if (aName.equals("lon")) {
						nodeLng = Double.parseDouble(aValue);
					}
				}
			}

			if (localName.equals("way")) {
				inWay = true;
				for (int i = 0; i < atts.getLength(); i++) {
					String aName = atts.getLocalName(i);
					String aValue = atts.getValue(i);

					if (aName.equals("id")) {
						wayId = Long.parseLong(aValue);
					}
				}
			}

			if (localName.equals("relation")) {
				inRel = true;
				for (int i = 0; i < atts.getLength(); i++) {
					String aName = atts.getLocalName(i);
					String aValue = atts.getValue(i);

					if (aName.equals("id")) {
						relId = Long.parseLong(aValue);
					}
				}
			}

			if (inWay && localName.equals("nd")) {
				for (int i = 0; i < atts.getLength(); i++) {
					String aName = atts.getLocalName(i);
					String aValue = atts.getValue(i);
					if (aName.equals("ref")) {
						wayNodeRefs.add(Long.parseLong(aValue));
					}
				}
			}

			if (inRel && localName.equals("member")) {
				for (int i = 0; i < atts.getLength(); i++) {
					String aName = atts.getLocalName(i);
					String aValue = atts.getValue(i);
					if (aName.equals("ref")) {
						relNodeRefs.add(Long.parseLong(aValue));
					}
				}
			}

			if (localName.equals("tag")) {
				KV t = new KV();
				for (int i = 0; i < atts.getLength(); i++) {
					String aName = atts.getLocalName(i);
					String aValue = atts.getValue(i);
					if (aName.equals("k")) {
						t.k = aValue;
					}
					if (aName.equals("v")) {
						t.v = aValue;
					}
				}
				tags.add(t);
			}
		}

		@Override
		public void endElement(String namespaceURI, String localName,
				String qName) throws SAXException {

			if (inNode && localName.equals("node")) {
				Place p = new Place(nodeLat, nodeLng);

				// If we happen to find a name tag in the tags, this node is
				// also a place of its own
				for (KV tag : tags) {
					if (tag.k.equals("name")) {
						p.name = tag.v;
					}
				}

				places.put(nodeId, p);

				inNode = false;
				nodeId = 0;
				nodeLat = 0;
				nodeLng = 0;
				tags.clear();
			}

			if (inWay && localName.equals("way")) {

				Place wayPlace = aggregatePositions(wayNodeRefs);
				if (wayPlace != null) {
					for (KV tag : tags) {
						if (tag.k.equals("name")) {
							wayPlace.name = tag.v;
							places.put(wayId, wayPlace);
						}
					}
				}

				inWay = false;
				tags.clear();
				wayId = 0;
				wayNodeRefs.clear();
			}

			if (inRel && localName.equals("relation")) {
				Place relPlace = aggregatePositions(relNodeRefs);
				if (relPlace != null) {
					places.put(relId, relPlace);
				}
				inRel = false;
				relId = 0;
				relNodeRefs.clear();
			}

		}

		@Override
		public void endDocument() {
			Map<String, Long> nameSet = new HashMap<String, Long>();
			Set<Long> killList = new HashSet<Long>();

			Iterator<Long> placeIter = places.keySet().iterator();
			while (placeIter.hasNext()) {
				Long placeId = placeIter.next();
				Place p = places.get(placeId);
				if (p.name.equals(Place.NN)) {
					placeIter.remove();
					log.debug("Unnamed place removed");
					continue;
				}
				if (nameSet.containsKey(p.name)) {
					placeIter.remove();
					log.debug("Duplicate place: " + p.name + " removed");
					// also remove first occurence, later, because we are not
					// allowed two concurred modifications in map!
					
					// TODO: check if this removes too much, e.g. streets
					killList.add(nameSet.get(p.name));
					continue;
				}
				nameSet.put(p.name, placeId);
			}
			for (Long killId : killList) {
				places.remove(killId);
			}
		}

		private Place aggregatePositions(Set<Long> ids) {
			int nodes = 0;
			double latSum = 0;
			double lngSum = 0;

			String name = Place.NN;

			for (Long nodeId : ids) {
				Place p = places.remove(nodeId);
				if (p == null) {
					log.debug("Reference to unknown node: " + nodeId);
					continue;
				}
				if (!p.name.equals(Place.NN) && name.equals(Place.NN)) {
					name = p.name;
				}
				latSum += p.lat;
				lngSum += p.lng;
				nodes++;
			}
			if (nodes > 0) {
				double aggrLat = latSum / nodes;
				double aggrLng = lngSum / nodes;
				return new Place(name, aggrLat, aggrLng);
			}
			return null;
		}
	}

	public static Map<Long, Place> osmParseSax(String zippedOsmXmlFile) {
		OsmHandler oh = new OsmHandler();

		log.info("Parsing " + zippedOsmXmlFile + " using SAX");
		try {
			SAXParserFactory spf = SAXParserFactory.newInstance();
			spf.setNamespaceAware(true);
			SAXParser saxParser = spf.newSAXParser();
			XMLReader xmlReader = saxParser.getXMLReader();
			xmlReader.setContentHandler(oh);
			xmlReader.parse(new InputSource(new GZIPInputStream(
					new FileInputStream(zippedOsmXmlFile))));
		} catch (Exception e) {
			log.warn("Some SAX Exception", e);
		}
		log.info("Found " + oh.getPlaces().size() + " places.");
		return oh.getPlaces();
	}

	public static void main(String[] args) {
		if (args.length < 1) {
			log.warn("Give me a gzipped osm xml file to parse as param!");
			System.exit(-1);
		}
		Map<Long, Place> places = osmParseSax(args[0]);

		for (Map.Entry<Long, Place> p : places.entrySet()) {
			p.getValue().save();
		}
	}
}
