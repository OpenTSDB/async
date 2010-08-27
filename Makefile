# Copyright 2010 StumbleUpon, Inc.
#
# This library is free software: you can redistribute it and/or modify it
# under the terms of the GNU Lesser General Public License as published
# by the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This library is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public License
# along with this library.  If not, see <http://www.gnu.org/licenses/>.

all: jar
# TODO(tsuna): Use automake to avoid relying on GNU make extensions.

top_builddir = build
package = com.stumbleupon.async
spec_title = StumbleUpon Async Library
spec_vendor = StumbleUpon, Inc.
spec_version = 1.0
suasync_SOURCES = \
	src/Callback.java	\
	src/Deferred.java	\
	src/DeferredGroupException.java	\
	src/DeferredGroup.java	\

suasync_LIBADD = libs/slf4j-api-1.6.0.jar
AM_JAVACFLAGS = -Xlint
package_dir = $(subst .,/,$(package))
classes=$(suasync_SOURCES:src/%.java=$(top_builddir)/$(package_dir)/%.class)
jar = $(top_builddir)/suasync-$(spec_version).jar

jar: $(jar)

get_dep_classpath = `echo $(suasync_LIBADD) | tr ' ' ':'`
$(top_builddir)/.javac-stamp: $(suasync_SOURCES)
	@mkdir -p $(top_builddir)
	javac $(AM_JAVACFLAGS) -cp $(get_dep_classpath) \
	  -d $(top_builddir) $(suasync_SOURCES)
	@touch "$@"

classes_with_nested_classes = $(classes:$(top_builddir)/%.class=%*.class)

pkg_version = \
  `git rev-list --pretty=format:%h HEAD --max-count=1 | sed 1d || echo unknown`
$(top_builddir)/manifest: $(top_builddir)/.javac-stamp .git/HEAD
	{ echo "Specification-Title: $(spec_title)"; \
          echo "Specification-Version: $(spec_version)"; \
          echo "Specification-Vendor: $(spec_vendor)"; \
          echo "Implementation-Title: $(package)"; \
          echo "Implementation-Version: $(pkg_version)"; \
          echo "Implementation-Vendor: $(spec_vendor)"; } >"$@"

$(jar): $(top_builddir)/manifest $(top_builddir)/.javac-stamp $(classes)
	cd $(top_builddir) && jar cfm `basename $(jar)` manifest $(classes_with_nested_classes) \
         || { rv=$$? && rm -f `basename $(jar)` && exit $$rv; }
#                       ^^^^^^^^^^^^^^^^^^^^^^^
# I've seen cases where `jar' exits with an error but leaves a partially built .jar file!

doc: $(top_builddir)/api/index.html

JDK_JAVADOC=http://download.oracle.com/javase/6/docs/api
$(top_builddir)/api/index.html: $(suasync_SOURCES)
	javadoc -d $(top_builddir)/api -classpath $(get_dep_classpath) \
          -link $(JDK_JAVADOC) $(suasync_SOURCES)
clean:
	@rm -f $(top_builddir)/.javac-stamp
	rm -f $(top_builddir)/manifest
	cd $(top_builddir) || exit 0 && rm -f $(classes_with_nested_classes)
	cd $(top_builddir) || exit 0 \
	  && test -d $(package_dir) || exit 0 \
	  && dir=$(package_dir) \
	  && while test x"$$dir" != x"$${dir%/*}"; do \
	       rmdir "$$dir" && dir=$${dir%/*} || break; \
	     done \
	  && rmdir "$$dir"

distclean: clean
	rm -f $(jar)
	rm -rf $(top_builddir)/api
	test ! -d $(top_builddir) || rmdir $(top_builddir)

.PHONY: all jar clean distclean doc check
