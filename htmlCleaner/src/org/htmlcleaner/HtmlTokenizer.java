/*  Copyright (c) 2006-2007, Vladimir Nikic
    All rights reserved.
	
    Redistribution and use of this software in source and binary forms, 
    with or without modification, are permitted provided that the following 
    conditions are met:
	
    * Redistributions of source code must retain the above
      copyright notice, this list of conditions and the
      following disclaimer.
	
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the
      following disclaimer in the documentation and/or other
      materials provided with the distribution.
	
    * The name of HtmlCleaner may not be used to endorse or promote 
      products derived from this software without specific prior
      written permission.

    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
    AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
    IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
    ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE 
    LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
    CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
    SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
    INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
    CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
    ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
    POSSIBILITY OF SUCH DAMAGE.
	
    You can contact Vladimir Nikic by sending e-mail to
    nikic_vladimir@yahoo.com. Please include the word "HtmlCleaner" in the
    subject line.
*/

package org.htmlcleaner;

import java.io.*;
import java.util.*;

/**
 * Main HTML tokenizer.
 * <p>It's task is to parse HTML and produce list of valid tokens:
 * open tag tokens, end tag tokens, contents (text) and comments.
 * As soon as new item is added to token list, cleaner is invoked
 * to clean current list at the end.</p>
 *
 * Created by: Vladimir Nikic.<br>
 * Date: November, 2006

 */
public class HtmlTokenizer {
	
	private final static int WORKING_BUFFER_SIZE = 1024;

    private BufferedReader _reader;
    private char[] _working = new char[WORKING_BUFFER_SIZE];
    
    private transient int _pos = 0;
    private transient int _len = -1;

    private transient StringBuffer _saved = new StringBuffer(512);

    private transient boolean _isLateForDoctype = false;
    private transient DoctypeToken _docType = null;
    private transient TagToken _currentTagToken = null;
    private transient List<BaseToken> _tokenList = new ArrayList<BaseToken>();
    private transient Set<String> _namespacePrefixes = new HashSet<String>();
    
    private boolean _asExpected = true;

    private boolean _isScriptContext = false;

    private HtmlCleaner cleaner;
    private CleanerProperties props;
    private CleanerTransformations transformations;

    /**
     * Constructor - cretes instance of the parser with specified content.
     * @param cleaner
     * @throws IOException
     */
    public HtmlTokenizer(HtmlCleaner cleaner, Reader reader) throws IOException {
        this._reader = new BufferedReader(reader);
        this.cleaner = cleaner;
        this.props = cleaner.getProperties();
        this.transformations = cleaner.getTransformations();
    }

    private void addToken(BaseToken token) {
        _tokenList.add(token);
        cleaner.makeTree( _tokenList, _tokenList.listIterator(_tokenList.size() - 1) );
    }

    private void readIfNeeded(int neededChars) throws IOException {
        if (_len == -1 && _pos + neededChars >= WORKING_BUFFER_SIZE) {
            int numToCopy = WORKING_BUFFER_SIZE - _pos;
            System.arraycopy(_working, _pos, _working, 0, numToCopy);
    		_pos = 0;

            int expected = WORKING_BUFFER_SIZE - numToCopy;
            int size = 0;
            int charsRead = 0;
            int offset = numToCopy;
            do {
                charsRead = _reader.read(_working, offset, expected);
                if (charsRead >= 0) {
                    size += charsRead;
                    offset += charsRead;
                    expected -= charsRead;
                }
            } while (charsRead >= 0 && expected > 0);

            if (expected > 0) {
    			_len = size + numToCopy;
            }

            // convert invalid XML characters to spaces
            for (int i = 0; i < (_len >= 0 ? _len : WORKING_BUFFER_SIZE); i++) {
                int ch = _working[i];
                if (ch >= 1 && ch <= 32 && ch != 10 && ch != 13) {
                    _working[i] = ' ';
                }
            }
        }
    }

    List<BaseToken> getTokenList() {
    	return this._tokenList;
    }

    Set<String> getNamespacePrefixes() {
        return _namespacePrefixes;
    }

    private void go() throws IOException {
    	_pos++;
    	readIfNeeded(0);
    }

    private void go(int step) throws IOException {
    	_pos += step;
    	readIfNeeded(step - 1);
    }

    /**
     * Checks if content starts with specified value at the current position.
     * @param value
     * @return true if starts with specified value, false otherwise.
     * @throws IOException
     */
    private boolean startsWith(String value) throws IOException {
        int valueLen = value.length();
        readIfNeeded(valueLen);
        if (_len >= 0 && _pos + valueLen  > _len) {
            return false;
        }

        for (int i = 0; i < valueLen; i++) {
        	char ch1 = Character.toLowerCase( value.charAt(i) );
        	char ch2 = Character.toLowerCase( _working[_pos + i] );
        	if (ch1 != ch2) {
        		return false;
        	}
        }

        return true;
    }

    /**
     * Checks if character at specified position is whitespace.
     * @param position
     * @return true is whitespace, false otherwise.
     */
    private boolean isWhitespace(int position) {
    	if (_len >= 0 && position >= _len) {
            return false;
        }

        return Character.isWhitespace( _working[position] );
    }

    /**
     * Checks if character at current runtime position is whitespace.
     * @return true is whitespace, false otherwise.
     */
    private boolean isWhitespace() {
        return isWhitespace(_pos);
    }

    /**
     * Checks if character at specified position is equal to specified char.
     * @param position
     * @param ch
     * @return true is equals, false otherwise.
     */
    private boolean isChar(int position, char ch) {
    	if (_len >= 0 && position >= _len) {
            return false;
        }

        return Character.toLowerCase(ch) == Character.toLowerCase(_working[position]);
    }

    /**
     * Checks if character at current runtime position is equal to specified char.
     * @param ch
     * @return true is equal, false otherwise.
     */
    private boolean isChar(char ch) {
        return isChar(_pos, ch);
    }

    /**
     * Checks if character at specified position can be identifier start.
     * @param position
     * @return true is may be identifier start, false otherwise.
     */
    private boolean isIdentifierStartChar(int position) {
    	if (_len >= 0 && position >= _len) {
            return false;
        }

        char ch = _working[position];
        return Character.isUnicodeIdentifierStart(ch);
    }

    /**
     * Checks if character at current runtime position can be identifier start.
     * @return true is may be identifier start, false otherwise.
     */
    private boolean isIdentifierStartChar() {
        return isIdentifierStartChar(_pos);
    }

    /**
     * Checks if character at current runtime position can be identifier part.
     * @return true is may be identifier part, false otherwise.
     */
    private boolean isIdentifierChar() {
    	if (_len >= 0 && _pos >= _len) {
            return false;
        }

        char ch = _working[_pos];
        return Character.isUnicodeIdentifierStart(ch) || Character.isDigit(ch) || Utils.isIdentifierHelperChar(ch);
    }

    /**
     * Checks if end of the content is reached.
     */
    private boolean isAllRead() {
        return _len >= 0 && _pos >= _len;
    }

    /**
     * Saves specified character to the temporary buffer.
     * @param ch
     */
    private void save(char ch) {
        _saved.append(ch);
    }

    /**
     * Saves character at current runtime position to the temporary buffer.
     */
    private void saveCurrent() {
        if (!isAllRead()) {
            save( _working[_pos] );
        }
    }

    /**
     * Saves specified number of characters at current runtime position to the temporary buffer.
     * @throws IOException
     */
    private void saveCurrent(int size) throws IOException {
    	readIfNeeded(size);
        int pos = _pos;
        while ( !isAllRead() && (size > 0) ) {
            save( _working[pos] );
            pos++;
            size--;
        }
    }

    /**
     * Skips whitespaces at current position and moves foreward until
     * non-whitespace character is found or the end of content is reached.
     * @throws IOException
     */
    private void skipWhitespaces() throws IOException {
        while ( !isAllRead() && isWhitespace() ) {
            saveCurrent();
            go();
        }
    }

    private boolean addSavedAsContent() {
        if (_saved.length() > 0) {
            addToken( new ContentToken(_saved.toString()) );
            _saved.delete(0, _saved.length());
            return true;
        }

        return false;
    }

    /**
     * Starts parsing HTML.
     * @throws IOException
     */
    void start() throws IOException {
    	// initialize runtime values
        _currentTagToken = null;
        _tokenList.clear();
        _asExpected = true;
        _isScriptContext = false;
        _isLateForDoctype = false;
        _namespacePrefixes.clear();

        this._pos = WORKING_BUFFER_SIZE;
        readIfNeeded(0);

        boolean isScriptEmpty = true;

        while ( !isAllRead() ) {
            // resets all the runtime values
            _saved.delete(0, _saved.length());
            _currentTagToken = null;
            _asExpected = true;

            // this is enough for making decision
            readIfNeeded(10);

            if (_isScriptContext) {
                if ( startsWith("</script") && (isWhitespace(_pos + 8) || isChar(_pos + 8, '>')) ) {
                    tagEnd();
                } else if ( isScriptEmpty && startsWith("<!--") ) {
                    comment();
                } else {
                    boolean isTokenAdded = content();
                    
                    if (isScriptEmpty && isTokenAdded) {
                        final BaseToken lastToken = (BaseToken) _tokenList.get(_tokenList.size() - 1);
                        if (lastToken != null) {
                            final String lastTokenAsString = lastToken.toString();
                            if (lastTokenAsString != null && lastTokenAsString.trim().length() > 0) {
                                isScriptEmpty = false;
                            }
                        }
                    }
                }
                if (!_isScriptContext) {
                    isScriptEmpty = true;
                }
            } else {
                if ( startsWith("<!doctype") ) {
                	if ( !_isLateForDoctype ) {
                		doctype();
                		_isLateForDoctype = true;
                	} else {
                		ignoreUntil('<');
                	}
                } else if ( startsWith("</") && isIdentifierStartChar(_pos + 2) ) {
                	_isLateForDoctype = true;
                    tagEnd();
                } else if ( startsWith("<!--") ) {
                    comment();
                } else if ( startsWith("<") && isIdentifierStartChar(_pos + 1) ) {
                	_isLateForDoctype = true;
                    tagStart();
                } else if ( props.isIgnoreQuestAndExclam() && (startsWith("<!") || startsWith("<?")) ) {
                    ignoreUntil('>');
                    if (isChar('>')) {
                        go();
                    }
                } else {
                    content();
                }
            }
        }

        _reader.close();
    }

    /**
     * Checks if specified tag name is one of the reserved tags: HTML, HEAD or BODY
     * @param tagName
     * @return
     */
    private boolean isReservedTag(String tagName) {
        return "html".equalsIgnoreCase(tagName) || "head".equalsIgnoreCase(tagName) || "body".equalsIgnoreCase(tagName);
    }

    /**
     * Parses start of the tag.
     * It expects that current position is at the "<" after which
     * the tag's name follows.
     * @throws IOException
     */
    private void tagStart() throws IOException {
        saveCurrent();
        go();

        if ( isAllRead() ) {
            return;
        }

        String tagName = identifier();

        TagTransformation tagTransformation = null;
        if (transformations != null && transformations.hasTransformationForTag(tagName)) {
            tagTransformation = transformations.getTransformation(tagName);
            if (tagTransformation != null) {
                tagName = tagTransformation.getDestTag();
            }
        }

        if (tagName != null) {
            ITagInfoProvider tagInfoProvider = cleaner.getTagInfoProvider();
            TagInfo tagInfo = tagInfoProvider.getTagInfo(tagName);
            if ( (tagInfo == null && !props.isOmitUnknownTags() && props.isTreatUnknownTagsAsContent() && !isReservedTag(tagName)) ||
                 (tagInfo != null && tagInfo.isDeprecated() && !props.isOmitDeprecatedTags() && props.isTreatDeprecatedTagsAsContent()) ) {
                content();
                return;
            }
        }

        TagNode tagNode = new TagNode(tagName, cleaner);
        _currentTagToken = tagNode;

        if (_asExpected) {
            skipWhitespaces();
            tagAttributes();

            if (tagName != null) {
                if (tagTransformation != null) {
                    tagNode.transformAttributes(tagTransformation);
                }
                addToken(_currentTagToken);
            }
            
            if ( isChar('>') ) {
            	go();
                if ( "script".equalsIgnoreCase(tagName) ) {
                    _isScriptContext = true;
                }
            } else if ( startsWith("/>") ) {
            	go(2);
            }

            _currentTagToken = null;
        } else {
        	addSavedAsContent();
        }
    }


    /**
     * Parses end of the tag.
     * It expects that current position is at the "<" after which
     * "/" and the tag's name follows.
     * @throws IOException
     */
    private void tagEnd() throws IOException {
        saveCurrent(2);
        go(2);

        if ( isAllRead() ) {
            return;
        }

        String tagName = identifier();
        if (transformations != null && transformations.hasTransformationForTag(tagName)) {
            TagTransformation tagTransformation = transformations.getTransformation(tagName);
            if (tagTransformation != null) {
                tagName = tagTransformation.getDestTag();
            }
        }

        if (tagName != null) {
            ITagInfoProvider tagInfoProvider = cleaner.getTagInfoProvider();
            TagInfo tagInfo = tagInfoProvider.getTagInfo(tagName);
            if ( (tagInfo == null && !props.isOmitUnknownTags() && props.isTreatUnknownTagsAsContent() && !isReservedTag(tagName)) ||
                 (tagInfo != null && tagInfo.isDeprecated() && !props.isOmitDeprecatedTags() && props.isTreatDeprecatedTagsAsContent()) ) {
                content();
                return;
            }
        }

        _currentTagToken = new EndTagToken(tagName);

        if (_asExpected) {
            skipWhitespaces();
            tagAttributes();

            if (tagName != null) {
                addToken(_currentTagToken);
            }

            if ( isChar('>') ) {
            	go();
            }

            if ( "script".equalsIgnoreCase(tagName) ) {
                _isScriptContext = false;
            }

            _currentTagToken = null;
        } else {
            addSavedAsContent();
        }
    }

    /**
     * Parses an identifier from the current position.
     * @throws IOException
     */
    private String identifier() throws IOException {
        _asExpected = true;

        if ( !isIdentifierStartChar() ) {
            _asExpected = false;
            return null;
        }

        StringBuffer identifierValue = new StringBuffer();

        while ( !isAllRead() && isIdentifierChar() ) {
        	
            saveCurrent();
           	identifierValue.append( _working[_pos] );
            go();
        }
        
        // strip invalid characters from the end
        while ( identifierValue.length() > 0 && Utils.isIdentifierHelperChar(identifierValue.charAt(identifierValue.length() - 1)) ) {
            identifierValue.deleteCharAt( identifierValue.length() - 1 );
        }

        if ( identifierValue.length() == 0 ) {
            return null;
        }

        String id = identifierValue.toString();

        int columnIndex = id.indexOf(':');
        if (columnIndex >= 0) {
            String prefix = id.substring(0, columnIndex);
            String suffix = id.substring(columnIndex + 1);
            int nextColumnIndex = suffix.indexOf(':');
            if (nextColumnIndex >= 0) {
                suffix = suffix.substring(0, nextColumnIndex);
            }
            if (props.isNamespacesAware()) {
                id = prefix + ":" + suffix;
                if ( !"xmlns".equalsIgnoreCase(prefix) ) {
                    _namespacePrefixes.add( prefix.toLowerCase() );
                }
            } else {
                id = suffix;
            }
        }

        return id;
    }


    /**
     * Parses an identifier from the current position.
     * @throws IOException
     */
    private String attrIdentifier() throws IOException {
        _asExpected = true;
        int idState = 0;
        if ( !isIdentifierStartChar() ) {
            _asExpected = false;
            return null;
        }

        StringBuffer identifierValue = new StringBuffer();

        while ( !isAllRead() && isIdentifierChar() ) {
        	
            saveCurrent();
            switch(idState) {
            case 0: // 
            	switch( _working[_pos] ) {
/****** DFA CONTENTS START ********/            	
            		case 'a':	// a
            		case 'A':
            			idState = 1; break;
            		case 'c':	// c
            		case 'C':
            			idState = 4; break;
            		case 'd':	// d
            		case 'D':
            			idState = 9; break;
            		case 'h':	// h
            		case 'H':
            			idState = 13; break;
            		case 'i':	// i
            		case 'I':
            			idState = 17; break;
            		case 'n':	// n
            		case 'N':
            			idState = 19; break;
            		case 'o':	// o
            		case 'O':
            			idState = 23; break;
            		case 's':	// s
            		case 'S':
            			idState = 30; break;
            		case 'v':	// v
            		case 'V':
            			idState = 40; break;
            		case 'w':	// w
            		case 'W':
            			idState = 45; break;
            		default:
            			idState = -1; break;
            	} break;
            case 1: // a
            	switch( _working[_pos] ) {
            		case 'l':	// al
            		case 'L':
            			idState = 2; break;
            		default:
            			idState = -1; break;
            	} break;
            case 2: // al
            	switch( _working[_pos] ) {
            		case 't':	// alt
            		case 'T':
            			idState = 3; break;
            		default:
            			idState = -1; break;
            	} break;
            case 3: // alt
            	switch( _working[_pos] ) {
            		default:
            			idState = -1; break;
            	} break;
            case 4: // c
            	switch( _working[_pos] ) {
            		case 'l':	// cl
            		case 'L':
            			idState = 5; break;
            		default:
            			idState = -1; break;
            	} break;
            case 5: // cl
            	switch( _working[_pos] ) {
            		case 'a':	// cla
            		case 'A':
            			idState = 6; break;
            		default:
            			idState = -1; break;
            	} break;
            case 6: // cla
            	switch( _working[_pos] ) {
            		case 's':	// clas
            		case 'S':
            			idState = 7; break;
            		default:
            			idState = -1; break;
            	} break;
            case 7: // clas
            	switch( _working[_pos] ) {
            		case 's':	// class
            		case 'S':
            			idState = 8; break;
            		default:
            			idState = -1; break;
            	} break;
            case 8: // class
            	switch( _working[_pos] ) {
            		default:
            			idState = -1; break;
            	} break;
            case 9: // d
            	switch( _working[_pos] ) {
            		case 'a':	// da
            		case 'A':
            			idState = 10; break;
            		default:
            			idState = -1; break;
            	} break;
            case 10: // da
            	switch( _working[_pos] ) {
            		case 't':	// dat
            		case 'T':
            			idState = 11; break;
            		default:
            			idState = -1; break;
            	} break;
            case 11: // dat
            	switch( _working[_pos] ) {
            		case 'a':	// data
            		case 'A':
            			idState = 12; break;
            		default:
            			idState = -1; break;
            	} break;
            case 12: // data
            	switch( _working[_pos] ) {
            		default:
            			idState = -1; break;
            	} break;
            case 13: // h
            	switch( _working[_pos] ) {
            		case 'r':	// hr
            		case 'R':
            			idState = 14; break;
            		default:
            			idState = -1; break;
            	} break;
            case 14: // hr
            	switch( _working[_pos] ) {
            		case 'e':	// hre
            		case 'E':
            			idState = 15; break;
            		default:
            			idState = -1; break;
            	} break;
            case 15: // hre
            	switch( _working[_pos] ) {
            		case 'f':	// href
            		case 'F':
            			idState = 16; break;
            		default:
            			idState = -1; break;
            	} break;
            case 16: // href
            	switch( _working[_pos] ) {
            		default:
            			idState = -1; break;
            	} break;
            case 17: // i
            	switch( _working[_pos] ) {
            		case 'd':	// id
            		case 'D':
            			idState = 18; break;
            		default:
            			idState = -1; break;
            	} break;
            case 18: // id
            	switch( _working[_pos] ) {
            		default:
            			idState = -1; break;
            	} break;
            case 19: // n
            	switch( _working[_pos] ) {
            		case 'a':	// na
            		case 'A':
            			idState = 20; break;
            		default:
            			idState = -1; break;
            	} break;
            case 20: // na
            	switch( _working[_pos] ) {
            		case 'm':	// nam
            		case 'M':
            			idState = 21; break;
            		default:
            			idState = -1; break;
            	} break;
            case 21: // nam
            	switch( _working[_pos] ) {
            		case 'e':	// name
            		case 'E':
            			idState = 22; break;
            		default:
            			idState = -1; break;
            	} break;
            case 22: // name
            	switch( _working[_pos] ) {
            		default:
            			idState = -1; break;
            	} break;
            case 23: // o
            	switch( _working[_pos] ) {
            		case 'n':	// on
            		case 'N':
            			idState = 24; break;
            		default:
            			idState = -1; break;
            	} break;
            case 24: // on
            	switch( _working[_pos] ) {
            		case 'c':	// onc
            		case 'C':
            			idState = 25; break;
            		default:
            			idState = -1; break;
            	} break;
            case 25: // onc
            	switch( _working[_pos] ) {
            		case 'l':	// oncl
            		case 'L':
            			idState = 26; break;
            		default:
            			idState = -1; break;
            	} break;
            case 26: // oncl
            	switch( _working[_pos] ) {
            		case 'i':	// oncli
            		case 'I':
            			idState = 27; break;
            		default:
            			idState = -1; break;
            	} break;
            case 27: // oncli
            	switch( _working[_pos] ) {
            		case 'c':	// onclic
            		case 'C':
            			idState = 28; break;
            		default:
            			idState = -1; break;
            	} break;
            case 28: // onclic
            	switch( _working[_pos] ) {
            		case 'k':	// onclick
            		case 'K':
            			idState = 29; break;
            		default:
            			idState = -1; break;
            	} break;
            case 29: // onclick
            	switch( _working[_pos] ) {
            		default:
            			idState = -1; break;
            	} break;
            case 30: // s
            	switch( _working[_pos] ) {
            		case 'i':	// si
            		case 'I':
            			idState = 31; break;
            		case 'r':	// sr
            		case 'R':
            			idState = 34; break;
            		case 't':	// st
            		case 'T':
            			idState = 36; break;
            		default:
            			idState = -1; break;
            	} break;
            case 31: // si
            	switch( _working[_pos] ) {
            		case 'z':	// siz
            		case 'Z':
            			idState = 32; break;
            		default:
            			idState = -1; break;
            	} break;
            case 32: // siz
            	switch( _working[_pos] ) {
            		case 'e':	// size
            		case 'E':
            			idState = 33; break;
            		default:
            			idState = -1; break;
            	} break;
            case 33: // size
            	switch( _working[_pos] ) {
            		default:
            			idState = -1; break;
            	} break;
            case 34: // sr
            	switch( _working[_pos] ) {
            		case 'c':	// src
            		case 'C':
            			idState = 35; break;
            		default:
            			idState = -1; break;
            	} break;
            case 35: // src
            	switch( _working[_pos] ) {
            		default:
            			idState = -1; break;
            	} break;
            case 36: // st
            	switch( _working[_pos] ) {
            		case 'y':	// sty
            		case 'Y':
            			idState = 37; break;
            		default:
            			idState = -1; break;
            	} break;
            case 37: // sty
            	switch( _working[_pos] ) {
            		case 'l':	// styl
            		case 'L':
            			idState = 38; break;
            		default:
            			idState = -1; break;
            	} break;
            case 38: // styl
            	switch( _working[_pos] ) {
            		case 'e':	// style
            		case 'E':
            			idState = 39; break;
            		default:
            			idState = -1; break;
            	} break;
            case 39: // style
            	switch( _working[_pos] ) {
            		default:
            			idState = -1; break;
            	} break;
            case 40: // v
            	switch( _working[_pos] ) {
            		case 'a':	// va
            		case 'A':
            			idState = 41; break;
            		default:
            			idState = -1; break;
            	} break;
            case 41: // va
            	switch( _working[_pos] ) {
            		case 'l':	// val
            		case 'L':
            			idState = 42; break;
            		default:
            			idState = -1; break;
            	} break;
            case 42: // val
            	switch( _working[_pos] ) {
            		case 'u':	// valu
            		case 'U':
            			idState = 43; break;
            		default:
            			idState = -1; break;
            	} break;
            case 43: // valu
            	switch( _working[_pos] ) {
            		case 'e':	// value
            		case 'E':
            			idState = 44; break;
            		default:
            			idState = -1; break;
            	} break;
            case 44: // value
            	switch( _working[_pos] ) {
            		default:
            			idState = -1; break;
            	} break;
            case 45: // w
            	switch( _working[_pos] ) {
            		case 'i':	// wi
            		case 'I':
            			idState = 46; break;
            		default:
            			idState = -1; break;
            	} break;
            case 46: // wi
            	switch( _working[_pos] ) {
            		case 'd':	// wid
            		case 'D':
            			idState = 47; break;
            		default:
            			idState = -1; break;
            	} break;
            case 47: // wid
            	switch( _working[_pos] ) {
            		case 't':	// widt
            		case 'T':
            			idState = 48; break;
            		default:
            			idState = -1; break;
            	} break;
            case 48: // widt
            	switch( _working[_pos] ) {
            		case 'h':	// width
            		case 'H':
            			idState = 49; break;
            		default:
            			idState = -1; break;
            	} break;
            case 49: // width
            	switch( _working[_pos] ) {
            		default:
            			idState = -1; break;
            	} break;
/****** DFA CONTENTS ENDS ********/
        case -1:
        		break;
        }
            
            if(idState != -1) {
            	identifierValue.append( _working[_pos] );
            } 
            go();
        }
        if(idState == -1) {
        	return null;
        }
        // strip invalid characters from the end
        while ( identifierValue.length() > 0 && Utils.isIdentifierHelperChar(identifierValue.charAt(identifierValue.length() - 1)) ) {
            identifierValue.deleteCharAt( identifierValue.length() - 1 );
        }

        if ( identifierValue.length() == 0 ) {
            return null;
        }

        String id = identifierValue.toString();

        int columnIndex = id.indexOf(':');
        if (columnIndex >= 0) {
            String prefix = id.substring(0, columnIndex);
            String suffix = id.substring(columnIndex + 1);
            int nextColumnIndex = suffix.indexOf(':');
            if (nextColumnIndex >= 0) {
                suffix = suffix.substring(0, nextColumnIndex);
            }
            if (props.isNamespacesAware()) {
                id = prefix + ":" + suffix;
                if ( !"xmlns".equalsIgnoreCase(prefix) ) {
                    _namespacePrefixes.add( prefix.toLowerCase() );
                }
            } else {
                id = suffix;
            }
        }

        return id;
    }

    
    /**
     * Parses list tag attributes from the current position.
     * @throws IOException
     */
    private void tagAttributes() throws IOException {
        while( !isAllRead() && _asExpected && !isChar('>') && !startsWith("/>") ) {
        	
            skipWhitespaces();
            String attName = attrIdentifier();

            if (!_asExpected) {
                if ( !isChar('<') && !isChar('>') && !startsWith("/>") ) {
                    saveCurrent();
                    go();
                }

                if (!isChar('<')) {
                    _asExpected = true;
                }

                continue;
            }

            String attValue = "";

            skipWhitespaces();
            if ( isChar('=') ) {
                saveCurrent();
                go();
                if(attName != null) {
                	attValue = attributeValue();
                } else {
                	skipAttributeValue();
                }
            } else if (CleanerProperties.BOOL_ATT_EMPTY.equals(props.booleanAttributeValues)) {
                attValue = "";
            } else if (CleanerProperties.BOOL_ATT_TRUE.equals(props.booleanAttributeValues)) {
                attValue = "true";
            } else {
                attValue = attName;
            }

            if (_asExpected && attName != null) {
                _currentTagToken.addAttribute(attName, attValue);
            }
            
        }
    }

    /**
     * Parses a single tag attribute - it is expected to be in one of the forms:
     * 		name=value
     * 		name="value"
     * 		name='value'
     * 		name
     * @throws IOException
     */
    private String attributeValue() throws IOException {
        skipWhitespaces();
        
        if ( isChar('<') || isChar('>') || startsWith("/>") ) {
        	return "";
        }

        boolean isQuoteMode = false;
        boolean isAposMode = false;

        StringBuffer result = new StringBuffer();

        if ( isChar('\'') ) {
            isAposMode = true;
            saveCurrent();
            go();
        } else if ( isChar('\"') ) {
            isQuoteMode = true;
            saveCurrent();
            go();
        }

        boolean isMultiWord = props.isAllowMultiWordAttributes();

        boolean allowHtml = props.isAllowHtmlInsideAttributes();

        while ( !isAllRead() &&
                ( (isAposMode && !isChar('\'') && (allowHtml || !isChar('>') && !isChar('<')) && (isMultiWord || !isWhitespace())) ||
                  (isQuoteMode && !isChar('\"') && (allowHtml || !isChar('>') && !isChar('<')) && (isMultiWord || !isWhitespace())) ||
                  (!isAposMode && !isQuoteMode && !isWhitespace() && !isChar('>') && !isChar('<'))
                )
              ) {
            result.append( _working[_pos] );
            saveCurrent();
            go();
        }

        if ( isChar('\'') && isAposMode ) {
            saveCurrent();
            go();
        } else if ( isChar('\"') && isQuoteMode ) {
            saveCurrent();
            go();
        }


        return result.toString();
    }

    /**
     * Parses a single tag attribute - it is expected to be in one of the forms:
     * 		name=value
     * 		name="value"
     * 		name='value'
     * 		name
     * @throws IOException
     */
    private void skipAttributeValue() throws IOException {
        skipWhitespaces();
        
        if ( isChar('<') || isChar('>') || startsWith("/>") ) {
        	return;
        }

        boolean isQuoteMode = false;
        boolean isAposMode = false;

        if ( isChar('\'') ) {
            isAposMode = true;
            saveCurrent();
            go();
        } else if ( isChar('\"') ) {
            isQuoteMode = true;
            saveCurrent();
            go();
        }

        boolean isMultiWord = props.isAllowMultiWordAttributes();

        boolean allowHtml = props.isAllowHtmlInsideAttributes();

        while ( !isAllRead() &&
                ( (isAposMode && !isChar('\'') && (allowHtml || !isChar('>') && !isChar('<')) && (isMultiWord || !isWhitespace())) ||
                  (isQuoteMode && !isChar('\"') && (allowHtml || !isChar('>') && !isChar('<')) && (isMultiWord || !isWhitespace())) ||
                  (!isAposMode && !isQuoteMode && !isWhitespace() && !isChar('>') && !isChar('<'))
                )
              ) {
            
            saveCurrent();
            go();
        }

        if ( isChar('\'') && isAposMode ) {
            saveCurrent();
            go();
        } else if ( isChar('\"') && isQuoteMode ) {
            saveCurrent();
            go();
        }


        return;
    }
    
    private boolean content() throws IOException {
        while ( !isAllRead() ) {
            saveCurrent();
            go();

            if ( isChar('<') ) {
                break;
            }
        }

        return addSavedAsContent();
    }

    private void ignoreUntil(char ch) throws IOException {
        while ( !isAllRead() ) {
        	go();
            if ( isChar(ch) ) {
                break;
            }
        }
    }

    private void comment() throws IOException {
    	go(4);
        while ( !isAllRead() && !startsWith("-->") ) {
            saveCurrent();
            go();
        }

        if (startsWith("-->")) {
        	go(3);
        }

        if (_saved.length() > 0) {
            if ( !props.isOmitComments() ) {
                String hyphenRepl = props.getHyphenReplacementInComment();
                String comment = _saved.toString().replaceAll("--", hyphenRepl + hyphenRepl);

        		if ( comment.length() > 0 && comment.charAt(0) == '-' ) {
        			comment = hyphenRepl + comment.substring(1);
        		}
        		int len = comment.length();
        		if ( len > 0 && comment.charAt(len - 1) == '-' ) {
        			comment = comment.substring(0, len - 1) + hyphenRepl;
        		}

        		addToken( new CommentToken(comment) );
        	}
            _saved.delete(0, _saved.length());
        }
    }
    
    private void doctype() throws IOException {
    	go(9);

    	skipWhitespaces();
    	String part1 = identifier();
	    skipWhitespaces();
	    String part2 = identifier();
	    skipWhitespaces();
	    String part3 = attributeValue();
	    skipWhitespaces();
	    String part4 = attributeValue();
	    
	    ignoreUntil('<');
	    
	    _docType = new DoctypeToken(part1, part2, part3, part4);
    }

    public DoctypeToken getDocType() {
        return _docType;
    }
    
}