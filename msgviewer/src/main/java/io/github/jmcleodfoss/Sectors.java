package io.github.jmcleodfoss.msgviewer;

import io.github.jmcleodfoss.voluminouspaginationskin.VoluminousPaginationSkin;
import io.github.jmcleodfoss.msg.MSG;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Pagination;
import javafx.scene.control.Tab;
import javafx.scene.layout.StackPane;
import javafx.util.Callback;

/** Display raw sectors. */
class Sectors extends Tab
{
	static private final String PROPNAME_SECTOR_TAB_TITLE = "Sectors";

	private ByteDataTable data;
	private Pagination pagination;

	private MSG msg;

	private UpdateInfoService updateInfoService;

	private class SuccessfulReadHandler implements EventHandler<WorkerStateEvent>
	{
		@Override
		public final void handle(WorkerStateEvent t)
		{
			byte[] sectorData = (byte[])t.getSource().getValue();
			if (sectorData != null){
				data.update(sectorData);
			}
		}
	}

	private class UpdateInfoService extends Service<byte[]>
	{
		private IntegerProperty pageIndex = new SimpleIntegerProperty();

		public IntegerProperty getPageIndexProperty()
		{
			return pageIndex;
		}

		public void setPageIndex(int pageIndex)
		{
			this.pageIndex.set(pageIndex);
		}

		public int getPageIndex()
		{
			return pageIndex.get();
		}

		protected Task<byte[]> createTask()
		{
			return new Task<byte[]>() {
				protected byte[] call()
				{
					return msg.getSector(getPageIndex());
				}
			};
		}
	}

	Sectors(LocalizedText localizer)
	{
		super(localizer.getText(PROPNAME_SECTOR_TAB_TITLE));

		pagination = new Pagination();
		pagination.setPageFactory(new Callback<Integer, Node>(){
			@Override
			public Node call(Integer pageIndex)
			{
				if (msg == null || pageIndex == null)
					return null;
				if (pageIndex < 0 || pageIndex >= pagination.getPageCount())
					return null;
				updateInfoService.setPageIndex(pageIndex);
				updateInfoService.setOnSucceeded(new SuccessfulReadHandler());
				updateInfoService.restart();

				data = new ByteDataTable(false);
				StackPane pane = new StackPane();
				pane.getChildren().add(data);
				return pane;
			}
		});
		pagination.setSkin(new VoluminousPaginationSkin(pagination));
		updateInfoService = new UpdateInfoService();

		setContent(pagination);
	}

	void update(MSG msg, LocalizedText localizer)
	{
		this.msg = msg;
		pagination.setPageCount(msg.numberOfSectors());
		pagination.setCurrentPageIndex(0);
	}
}
