/**
 * Part of the CCNx Java Library.
 *
 * Copyright (C) 2008, 2009 Palo Alto Research Center, Inc.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation. 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. You should have received
 * a copy of the GNU Lesser General Public License along with this library;
 * if not, write to the Free Software Foundation, Inc., 51 Franklin Street,
 * Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.ccnx.ccn.io;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

import javax.xml.stream.XMLStreamException;

import org.ccnx.ccn.impl.CCNFlowControl;
import org.ccnx.ccn.impl.CCNSegmenter;
import org.ccnx.ccn.impl.security.crypto.CCNBlockSigner;
import org.ccnx.ccn.impl.security.crypto.ContentKeys;
import org.ccnx.ccn.profiles.SegmentationProfile;
import org.ccnx.ccn.profiles.SegmentationProfile.SegmentNumberType;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.KeyLocator;
import org.ccnx.ccn.protocol.PublisherPublicKeyDigest;
import org.ccnx.ccn.protocol.SignedInfo;
import org.ccnx.ccn.protocol.SignedInfo.ContentType;


/**
 * This class acts as a packet-oriented stream of data. It might be
 * better implemented as a subclass of DatagramSocket. Given a base name
 * and signing information, it writes content blocks under that base name,
 * where each block is named according to the base name concatenated with a
 * sequence number identifying the specific block. 
 * 
 * Each call to write writes one or more individual ContentObjects. The
 * maximum size is given by parameters of the segmenter used; if buffers
 * are larger than that size they are output as multiple fragments.
 * 
 * It does offer flexible content name increment options. The creator
 * can specify an initial block id (default is 0), and an increment (default 1)
 * for fixed-width blocks, or blocks can be identified by byte offset
 * in the running stream, or by another integer metric (e.g. time offset),
 * by supplying a multiplier to conver the byte offset into a metric value.
 * Finally, writers can specify the block identifier with a write.
 * 
 * @author smetters
 *
 */
public class CCNBlockOutputStream extends CCNAbstractOutputStream {

	protected SignedInfo.ContentType _type;
	
	/**
	 * Default, fixed increment, sequential-numbered blocks (unless overridden on write).
	 * @param name
	 * @param publisher
	 * @param locator
	 * @param signingKey
	 * @param library
	 * @throws XMLStreamException
	 * @throws IOException
	 */
	public CCNBlockOutputStream(ContentName baseName, SignedInfo.ContentType type,
								PublisherPublicKeyDigest publisher,
								CCNFlowControl flowControl)
								throws XMLStreamException, IOException {
		super(null, publisher, new CCNSegmenter(flowControl, new CCNBlockSigner()));
		init(baseName, type);
	}

	public CCNBlockOutputStream(ContentName baseName, SignedInfo.ContentType type,
			KeyLocator locator, PublisherPublicKeyDigest publisher,
			ContentKeys keys, CCNFlowControl flowControl)
			throws XMLStreamException, IOException {
		super(locator, publisher, new CCNSegmenter(flowControl, new CCNBlockSigner(), keys));
		init(baseName, type);
	}

	private void init(ContentName baseName, SignedInfo.ContentType type) {
		_type = type;

		ContentName nameToOpen = baseName;
		
		// If someone gave us a fragment name, at least strip that.
		if (SegmentationProfile.isSegment(nameToOpen)) {
			// DKS TODO: should we do this?
			nameToOpen = SegmentationProfile.segmentRoot(nameToOpen);
		}

		// Don't go looking for or adding versions. Might be unversioned,
		// unfragmented content (e.g. RTP streams). Assume caller knows
		// what name they want.
		_baseName = nameToOpen;
	}
	
	public CCNBlockOutputStream(ContentName baseName, SignedInfo.ContentType type) throws XMLStreamException, IOException {
		this(baseName, type, null, null);
	}
		
	public void useByteCountSequenceNumbers() {
		getSegmenter().setSequenceType(SegmentNumberType.SEGMENT_BYTE_COUNT);
		getSegmenter().setByteScale(1);
	}

	public void useFixedIncrementSequenceNumbers(int increment) {
		getSegmenter().setSequenceType(SegmentNumberType.SEGMENT_FIXED_INCREMENT);
		getSegmenter().setBlockIncrement(increment);
	}

	public void useScaledByteCountSequenceNumbers(int scale) {
		getSegmenter().setSequenceType(SegmentNumberType.SEGMENT_BYTE_COUNT);
		getSegmenter().setByteScale(scale);
	}
	
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		try {
			getSegmenter().put(_baseName, b, off, len, false, ContentType.DATA, null, null, null);
		} catch (InvalidKeyException e) {
			throw new IOException("Cannot sign content -- invalid key!: " + e.getMessage());
		} catch (SignatureException e) {
			throw new IOException("Cannot sign content -- signature failure!: " + e.getMessage());
		} catch (NoSuchAlgorithmException e) {
			throw new IOException("Cannot sign content -- unknown algorithm!: " + e.getMessage());
		} catch (InvalidAlgorithmParameterException e) {
			throw new IOException("Cannot encrypt content -- bad algorithm parameter!: " + e.getMessage());
		} 
	}

}
