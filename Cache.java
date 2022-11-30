import java.util.*;

public class Cache {

	// each block's size in this cache
	private int blockSize;

	// list of pages
	private Vector<byte[]> pages;

	// next victim number
	private int victim;

	private class Entry {

		// constant for invalid page
		public static final int INVALID = -1;

		// shows that a block has been recently used.
		public boolean refbit;

		// shows that a block has been modified.
		public boolean dirtybit;

		// keeps track of open entry or in-use
		public int frame;

		public Entry() {
			refbit = false;
			dirtybit = false;
			frame = INVALID;
		}
	}

	// table to hold all of the pages
	private Entry[] pageTable = null;

	public Cache(int blockSize, int cacheBlocks) {
		this.blockSize = blockSize;
		pageTable = new Entry[cacheBlocks];
		victim = 0;
		pages = new Vector<byte[]>();

		// initialize pageTable with empty entries. Pair each entry with
		// a corresponding block of size blockSize in pages
		for (int i = 0; i < cacheBlocks; i++) {
			pageTable[i] = new Entry();
			pages.add(i, new byte[this.blockSize]);
		}
	}

	private int nextVictim() {

		// search for invalid page in pageTable
		for (int i = 0; i < pageTable.length; i++) {

			// if an invalid page is found, return position in pageTable
			if (pageTable[i].frame == Entry.INVALID) {
				return i;
			}
		}
		// if no invalid page was found, continue to find victim
		// start second chance algo from textbook & class explanation
		while (true) {
			victim = (victim + 1) % pageTable.length;

			// if we find a non-recently-used page, return it to caller
			if (pageTable[victim].refbit == false)
				return victim;
			// otherwise, keep searching and mark this one as used
			pageTable[victim].refbit = false;
		}
	}

	private void writeBack(int victimEntry) {
		// if the frame is not invalid and the block has not been modified, write to the
		// memory and set the show this page as non-modified via dirtybit
		if (pageTable[victimEntry].frame != Entry.INVALID && pageTable[victimEntry].dirtybit == true) {
			SysLib.rawwrite(pageTable[victimEntry].frame, pages.get(victimEntry));
			pageTable[victimEntry].dirtybit = false;
		}
	}

	public synchronized boolean read(int blockId, byte buffer[]) {
		if (blockId < 0) {
			SysLib.cerr("threadOS: a wrong blockId for cread\n");
			return false;
		}

		// locate a valid page to read
		for (int i = 0; i < pageTable.length; i++) {

			// cache hit!!
			if (pageTable[i].frame == blockId) {

				// copy pages[i] to buffer
				System.arraycopy(pages.get(i), 0, buffer, 0, this.blockSize);
				pageTable[i].refbit = true; // mark recently-used
				return true; // leave
			}
		}

		// page miss
		// find an invalid page
		// if no invalid page is found, all pages are full.
		// seek for a victim
		// nextVictim() finds an invalid page and returns it or
		// finds the next victim
		int victimEntry = nextVictim();
		// write back a dirty copy
		writeBack(victimEntry);
		// read a requested block from disk
		SysLib.rawread(blockId, buffer);
		// cache it
		// copy pages[victimEntry] to buffer
		System.arraycopy(pages.get(victimEntry), 0, buffer, 0, this.blockSize);
		pageTable[victimEntry].frame = blockId; // set frame to current block's ID 
		pageTable[victimEntry].refbit = true; // mark recently-used
		return true;
	}

	public synchronized boolean write(int blockId, byte buffer[]) {
		if (blockId < 0) {
			SysLib.cerr("threadOS: a wrong blockId for cwrite\n");
			return false;
		}

		// locate valid page to write
		for (int i = 0; i < pageTable.length; i++) {

			// cache hit
			if (pageTable[i].frame == blockId) {

				// copy buffer to pages[i]
				System.arraycopy(buffer, 0, pages.get(i), 0, this.blockSize);
				pageTable[i].refbit = true; // mark as recently used
				pageTable[i].dirtybit = true; // mark as modified
				return true; // leave
			}
		}

		// page miss
		// find an invalid page
		// if no invalid page is found, all pages are full.
		// seek for a victim
		// nextVictim() finds an invalid page and returns it or
		// finds the next victim
		int victimEntry = nextVictim();

		// write back a dirty copy
		writeBack(victimEntry);

		// cache it but not write through.
		// copy buffer to pages[victimEntry]
		System.arraycopy(buffer, 0, pages.get(victimEntry), 0, this.blockSize);
		pageTable[victimEntry].frame = blockId; // set frame to current block's ID
		pageTable[victimEntry].refbit = true; // mark as recently used
		pageTable[victimEntry].dirtybit = true; // mark as modified
		return true; // leave
	}

	public synchronized void sync() {
		for (int i = 0; i < pageTable.length; i++) {
			writeBack(i);
		}
		SysLib.sync();
	}

	public synchronized void flush() {
		for (int i = 0; i < pageTable.length; i++) {
			writeBack(i);
			pageTable[i].refbit = false;
			pageTable[i].frame = Entry.INVALID;
		}
		SysLib.sync();
	}
}