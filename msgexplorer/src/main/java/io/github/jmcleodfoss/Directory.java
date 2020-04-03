package io.github.jmcleodfoss.msgexplorer;

import io.github.jmcleodfoss.msg.DirectoryEntryData;
import io.github.jmcleodfoss.msg.MSG;

import java.util.Iterator;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;

class Directory extends Tab
{
	static private final String PROPNAME_DIRECTORY_TAB_TITLE = "Directory";
	static private final String PROPNAME_DIRECTORY_CONTENTS_READABLE = "HumanReadable";
	static private final String PROPNAME_DIRECTORY_CONTENTS_KEY = "Description";
	static private final String PROPNAME_DIRECTORY_CONTENTS_VALUE = "DirContentValue";
	static private final String PROPNAME_DIRECTORY_CONTENTS_RAW = "DirContentRaw";

	/** The overall pane for all directory info. Left side is the directory
	*   tree, and the right side is the information about the selected node
	*   (if any). The right side is invisible if no node is selected.
	*/
	private SplitPane containingPane;

	/** The directory tree */
	private StackPane treePane;

	/** The information about the selected node (if any). The top is the
	*   directory entry contents (a tabbed pane allowing display of human-
	*   readable text or raw bytes), and the bottom is the "file"
	*   for this directory entry (if any).
	*/
	private SplitPane infoPane;

	/** The tabbed pane showing the directory entry contents. */
	private TabPane contentTabs;

	private KVPTableTab<String, String> descriptionTab;
	private Tab dataTab;
	private ByteDataTable data;
	private TabPane filePane;
	private Tab fileContentsRawTab;
	private ByteDataTable fileContentsRaw;
	private Tab fileContentsTextTab;
	private Text fileContentsText;

	private TreeView<DirectoryEntryData> tree;

	private MSG msg;
	private LocalizedText localizer;

	private UpdateInfoService updateInfoService;

	private class SelectionChangeListener implements ChangeListener<TreeItem<DirectoryEntryData>>
	{
		@Override
		public void changed(ObservableValue<? extends TreeItem<DirectoryEntryData>> observable, TreeItem<DirectoryEntryData> oldVal, TreeItem<DirectoryEntryData> newVal)
		{
			final DirectoryEntryData de = newVal.getValue();
			descriptionTab.update(de.kvps, localizer);
			data.update(msg.getRawDirectoryEntry(de.entry));

			// Header points to the mini stream, so skip it.
			if (de.entry != 0) {
				updateInfoService.setEntryIndex(de.entry);
				updateInfoService.setOnSucceeded(new SuccessfulReadHandler());
				updateInfoService.restart();
			} else {
				fileContentsRaw.clear();
				fileContentsText.setText("");
			}
		}
	}

	private class SuccessfulReadHandler implements EventHandler<WorkerStateEvent>
	{
		@Override
		public final void handle(WorkerStateEvent t)
		{
			byte[] fileData = (byte[])t.getSource().getValue();
			if (fileData != null) {
				fileContentsRaw.update(fileData);
				if (msg.isTextData(updateInfoService.getEntryIndex())) {
					fileContentsText.setText(msg.convertFileToString(updateInfoService.getEntryIndex(), fileData));
				} else {
					fileContentsText.setText("");
				}
			} else {
				fileContentsRaw.clear();
				fileContentsText.setText("");
			}
		}
	}

	private class UpdateInfoService extends Service<byte[]>
	{
		private IntegerProperty entryIndex = new SimpleIntegerProperty();
		public IntegerProperty getEntryIndexProperty()
		{
			return entryIndex;
		}
		public void setEntryIndex(int entryIndex)
		{
			this.entryIndex.set(entryIndex);
		}
		public int getEntryIndex()
		{
			return entryIndex.get();
		}
		protected Task<byte[]> createTask()
		{
			return new Task<byte[]>() {
				protected byte[] call()
				{
					return msg.getFile(getEntryIndex());
				}
			};
		}
	}

	Directory(LocalizedText localizer)
	{
		super(localizer.getText(PROPNAME_DIRECTORY_TAB_TITLE));
		this.localizer = localizer;
		updateInfoService = new UpdateInfoService();

		tree = new TreeView<DirectoryEntryData>();
		tree.getSelectionModel().selectedItemProperty().addListener(new SelectionChangeListener());
		treePane = new StackPane();
		treePane.getChildren().add(tree);

		descriptionTab = new KVPTableTab<String, String>(
			localizer.getText(PROPNAME_DIRECTORY_CONTENTS_READABLE),
			localizer.getText(PROPNAME_DIRECTORY_CONTENTS_KEY),
			localizer.getText(PROPNAME_DIRECTORY_CONTENTS_VALUE));
		descriptionTab.update(MSG.getDirectoryEntryKeys(), localizer);

		data = new ByteDataTable(false);
		dataTab = new Tab(localizer.getText(PROPNAME_DIRECTORY_CONTENTS_RAW));
		dataTab.setContent(data);

		contentTabs = new TabPane(descriptionTab, dataTab);
		contentTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

		fileContentsRaw = new ByteDataTable(true);
		fileContentsRaw.setUnicodeVisible(false);

		fileContentsRawTab = new Tab("Raw");
		fileContentsRawTab.setContent(fileContentsRaw);

		fileContentsText = new Text();

		fileContentsTextTab = new Tab("Text");
		fileContentsTextTab.setContent(fileContentsText);

		filePane = new TabPane(fileContentsRawTab, fileContentsTextTab);
		filePane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

		infoPane = new SplitPane();
		infoPane.getItems().addAll(contentTabs, filePane);
		infoPane.setOrientation(Orientation.VERTICAL);
		infoPane.setDividerPositions(0.5f);

		containingPane = new SplitPane();
		containingPane.getItems().addAll(treePane, infoPane);
		containingPane.setDividerPositions(0.4f);

		setContent(containingPane);
	}

	private TreeItem<DirectoryEntryData> addEntry(MSG msg, int entry)
	{
		DirectoryEntryData ded = msg.getDirectoryEntryData(entry);
		TreeItem<DirectoryEntryData> node = new TreeItem<DirectoryEntryData>(ded);
		java.util.Iterator<Integer> iter = ded.children.iterator();
		while (iter.hasNext())
			node.getChildren().add(addEntry(msg, iter.next()));
		return node;
	}

	void update(MSG msg, LocalizedText localizer)
	{
		tree.setRoot(addEntry(msg, 0));
		tree.getTreeItem(0).setExpanded(true);
		this.msg = msg;
	}
}