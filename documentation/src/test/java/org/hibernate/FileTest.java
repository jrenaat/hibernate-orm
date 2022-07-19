/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * @author Jan Schatteman
 */
public class FileTest {

	/**
	 * Examples of links found
	 * <a href="# ...>		> check if this file contains the anchor
	 * <a class="anchor" href="# ...>	> id. as above
	 * <a id="_footnoteref_1" class="footnote" href="#_footnotedef_1 ...>	> id. as above
	 * 		=> search inside the current file
	 *
	 * <a href="chapters/query/criteria/QueryLanguage.html#query-language"> ...>	> check the referenced html file exists and contains the anchor
	 * <a href="generated/GeneratedValues.html#generated-values-guide"> ...>	-> id. as above
	 * <a href="../../userguide/html_single/Hibernate_User_Guide.html#bootstrap-native ...>	-> id. as above
	 * 		=> search inside the file referenced by the indicated path, relative to the current file's path
	 *
	 * <a href="https:// ...>	>check the external url exists
	 */

	@Test
	public void listFiles() {
		System.Logger log = System.getLogger( "FileTest" );
		String dir = "/home/jschatte/Dev/Hibernate/forks/hibernate-orm/documentation/target/asciidoc";

		List<String> files = new ArrayList<>();
		List<Path> paths;

		try (Stream<Path> stream = Files.walk( Paths.get( dir )) ) {
			PathMatcher pm = FileSystems.getDefault().getPathMatcher( "glob:**.html" );
//				files = stream.filter( p -> pm.matches( p ) )
//						.map( p -> p.toString() )
//						.collect( Collectors.toList() );
				paths = stream.collect( Collectors.toList() );
				for (Path p : paths) {
					if (pm.matches( p )) files.add( p.toString() );
				}
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		for (String s : files) {
			File f = new File(s);
			try {
				Document doc = Jsoup.parse( f, "UTF-8" );
				// all links
				Elements elms = doc.select( "a[href]");
				for ( Element e : elms) {
					System.out.println( e );
				}
				// local refs
//				Elements elms2 = doc.select( "a[href^=#]");
				int i = 0;
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

}
