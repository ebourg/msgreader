#!/bin/bash
# Process pstreader/extras/properties.csv to get properties for msgreader
cat << END_HEADER > PropertyTags.java
// DO NOT EDIT THIS FILE
// Automatically generated on $(date) by msgreader/extras/getpropertytags.sh
// Any changes must be made to that file.
package io.github.jmcleodfoss.msg;

/** Known Property tags
*	@see <a href="https://github.com/Jmcleodfoss/pstreader/blob/master/extras/properties.csv">pstreader properties.pst</a>
*	@see <a href="https://github.com/Jmcleodfoss/msgreader/blob/master/extras/getpropertytags.sh">getpropertytags.sh</a>
*	@see <a href="https://docs.microsoft.com/en-us/openspecs/exchange_server_protocols/ms-oxprops/f6ab1613-aefe-447d-a49c-18217230b148">MS-OXPROPS</a>
*/

public class PropertyTags
{
END_HEADER
curl https://raw.githubusercontent.com/Jmcleodfoss/pstreader/master/extras/properties.csv | sort -t , -k 2 | sed '
	${
		i\

		i\
	static final java.util.HashMap<Integer, String> tags = new java.util.HashMap<Integer, String>();
		i\
	static {
		g
		a\
	}
	}
	/^\(PidTag[^,]*\),\([^,]*\),\([^,]*\),0x\(.*\)\r$/s//\	static final public int \1 = \2\4;/p
	/^.*static final public int \([^ ]*\).*$/{
		s//\	\	tags.put(\1, "\1");/
		H
		d
	}
	/n\/a/d
	/^PidLid/d
	' >> PropertyTags.java

cat << END_FOOTER >> PropertyTags.java

	public static void main(String[] args)
	{
		java.util.Iterator<Integer> iter = PropertyTags.tags.keySet().iterator();
		while (iter.hasNext()) {
			Integer t = iter.next();
			System.out.printf("0x%08x: %s\n", t, PropertyTags.tags.get(t));
		}
	}
}
END_FOOTER
