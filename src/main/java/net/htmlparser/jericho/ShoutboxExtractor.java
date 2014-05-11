package net.htmlparser.jericho;

public class ShoutboxExtractor {
	public static String extract(Segment segment) {
		StringBuilder sb = new StringBuilder(segment.length());
		NodeIterator ni = new NodeIterator(segment);
		while (ni.hasNext()) {
			Segment seg = ni.next();
			if (seg instanceof Tag) {
				Tag tag = (Tag) seg;
				if (tag.getName() == HTMLElementName.IMG) {
					sb.append(' ')
							.append(((StartTag) tag).getElement()
									.getAttributeValue("alt")).append(' ');
					ni.skipToPos(tag.getElement().getEnd());
				}
				if (tag.getName() == HTMLElementName.A) {
					sb.append(' ')
							.append(((StartTag) tag).getElement()
									.getAttributeValue("href")).append(' ');
					ni.skipToPos(tag.getElement().getEnd());
				}
				if (tag.getName() == HTMLElementName.BR
						|| !HTMLElements.getInlineLevelElementNames().contains(
								tag.getName()))
					sb.append(' ');
			} else {
				sb.append(seg);
			}
		}
		return CharacterReference.decodeCollapseWhiteSpace(sb.toString());
	}
}
