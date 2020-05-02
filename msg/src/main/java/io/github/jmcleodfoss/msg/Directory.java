package io.github.jmcleodfoss.msg;

/** The directory structure in the CFB.
*	@see <a href="https://docs.microsoft.com/en-us/openspecs/windows_protocols/ms-cfb/a94d7445-c4be-49cd-b6b9-2f4abc663817">MS-CFB 2.6 Compound File Directory Sectors</a>
*/
class Directory {

	/** The directory entries. */
	final java.util.ArrayList<DirectoryEntry> entries;

	/** The index to the named properties directory entry */
	final DirectoryEntry namedPropertiesMappingEntry;

	/** The properties entries in the directory (including both the main message properties and those in any attachments and recipients */
	final java.util.ArrayList<DirectoryEntry> propertyEntries;

	/** The parents of each entry */
	final java.util.HashMap<DirectoryEntry, DirectoryEntry> parents;

	/** Ad hoc utility class to collect information from DirectoryEntry construction for use in final variables in Directory.
	*	@see Directory#Directory
	*/
	class ConstructorData {
		/** The entry containing the Named Properties info
		*	@see Directory#namedPropertiesMappingEntry
		*/
		DirectoryEntry namedPropertiesMappingEntry;

		/** The properties entries in the directory (including both the main message properties and those in any attachments and recipients
		*	@see Directory#propertyEntries
		*/
		java.util.ArrayList<DirectoryEntry> propertyEntries;

		/** Construct an object by initializing its constituent ArrayLists */
		ConstructorData()
		{
			propertyEntries = new java.util.ArrayList<DirectoryEntry>();
		}
	}

	/** Construct a directory object.
	*	@param	byteBuffer	The CFB file
	*	@param	header		The CFB header
	*	@param	fat		The CFB file allocation table
	*	@throws	java.io.IOException	An error was encountered reading the directory structure.
	*	@see DirectoryEntry#factory
	*/
	Directory(java.nio.ByteBuffer byteBuffer, Header header, FAT fat)
	throws
		java.io.IOException
	{
		entries = new java.util.ArrayList<DirectoryEntry>();
		java.util.Iterator<Integer> chain = fat.chainIterator(header.firstDirectorySectorLocation);
		ConstructorData cd = new ConstructorData();
		while(chain.hasNext()){
			int dirSector = chain.next();
			byteBuffer.position(header.offset(dirSector));
			for (int i = 0; i < header.sectorSize / DirectoryEntry.SIZE; ++i)
				entries.add(DirectoryEntry.factory(byteBuffer, cd));
		}
		namedPropertiesMappingEntry = cd.namedPropertiesMappingEntry;
		propertyEntries = cd.propertyEntries;
		parents = new java.util.HashMap<DirectoryEntry, DirectoryEntry>();
		setParent(entries.get(0));
	}

	/** Collect all siblings and self for the given childIndex.
	*	@param	siblings	The list of children of childIndex's parent
	*	@param	child		The given child for the parent we are collecting the children of.
	*	@see <a href="https://docs.microsoft.com/en-us/openspecs/windows_protocols/ms-cfb/a94d7445-c4be-49cd-b6b9-2f4abc663817">MS-CFB 2.6 Compound File Directory Sectors</a>
	*/
	private void addSiblings(java.util.ArrayList<DirectoryEntry> siblings, DirectoryEntry child)
	{
		if (child.leftSiblingId != Sector.FREESECT)
			addSiblings(siblings, entries.get(child.leftSiblingId));
		siblings.add(child);
		if (child.rightSiblingId != Sector.FREESECT)
			addSiblings(siblings, entries.get(child.rightSiblingId));
	}

	/** Get the sibling of the given entry index which has the specified property.
	*	@param	entry		The entry to find the sibling of
	*	@param	filename	The name of the sibling to look for
	*	@return	The requested sibling, if found. null if the sibling was not found.
	*/
	DirectoryEntry getSiblingByName(DirectoryEntry entry, String filename)
	{
		DirectoryEntry parent = parents.get(entry);
		if (parent == null)
			return null;
		java.util.ArrayList<DirectoryEntry> children = getChildren(parent);
		for (DirectoryEntry child : children) {
			if (child.directoryEntryName.equals(filename))
				return child;
		}
		return null;
	}

	/** Get the first generation child nodes for a given node.
	*	@param	parent	The directory entry of the parent we want to find the children of, if any.
	*	@return	The (possibly empty) list of children of the directory entry for parentIndex.
	*	@see <a href="https://docs.microsoft.com/en-us/openspecs/windows_protocols/ms-cfb/a94d7445-c4be-49cd-b6b9-2f4abc663817">MS-CFB 2.6 Compound File Directory Sectors</a>
	*/
	java.util.ArrayList<DirectoryEntry> getChildren(DirectoryEntry parent)
	{
		java.util.ArrayList<DirectoryEntry> children = new java.util.ArrayList<DirectoryEntry>();
		if (parent.childId != Sector.FREESECT)
			addSiblings(children, entries.get(parent.childId));
		return children;
	}

	/** Get an iterator through the directory entries.
	*	@return	An iterator through the directory entries
	*/
	java.util.Iterator<DirectoryEntry> iterator()
	{
		return entries.iterator();
	}

	/** Set the parent node for each child node
	*	@param	parent	The parent node
	*/
	private void setParent(DirectoryEntry parent)
	{
		java.util.ArrayList<DirectoryEntry> children = getChildren(parent);
		for (java.util.Iterator<DirectoryEntry> iter = children.iterator(); iter.hasNext(); ){
			DirectoryEntry de = iter.next();
			parents.put(de, parent);
			setParent(de);
		}
	}

	/** Test this class by printing out the directory and the list of children for each node.
	*	@param	args	The command line arguments to the test application; this is expected to be a MSG file to be processed and a log level.
	*/
	public static void main(String[] args)
	{
		if (args.length == 0) {
			System.out.println("use:\n\tjava io.github.jmcleodfoss.mst.Directory msg-file [log-level]");
			System.exit(1);
		}
		try {
			java.io.File file = new java.io.File(args[0]);
			java.io.FileInputStream stream = new java.io.FileInputStream(file);
			java.nio.channels.FileChannel fc = stream.getChannel();
			java.nio.MappedByteBuffer mbb = fc.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, fc.size());
			mbb.order(java.nio.ByteOrder.LITTLE_ENDIAN);

			Header header = new Header(mbb, fc.size());
			DIFAT difat = new DIFAT(mbb, header);
			FAT fat = new FAT(mbb, header, difat);
			Directory directory = new Directory(mbb, header, fat);

			java.util.Iterator<DirectoryEntry> iterator = directory.iterator();
			int i = 0;
			while (iterator.hasNext())
				System.out.printf("0x%02x: %s\n", i++, iterator.next().toString());

			System.out.println("\n");
			for (i = 0; i < directory.entries.size(); ++i){
				java.util.ArrayList<DirectoryEntry> children = directory.getChildren(directory.entries.get(i));
				if (children.size() > 0){
					System.out.printf("Children of 0x%02x:\n", i);
					java.util.Iterator<DirectoryEntry> childIterator = children.iterator();
					while (childIterator.hasNext())
						System.out.println("\t" + childIterator.next());
				}
			}
		} catch (final Exception e) {
			e.printStackTrace(System.out);
		}
	}
}
