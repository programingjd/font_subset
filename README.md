![jcenter](https://img.shields.io/badge/_jcenter_-_1.0.0-6688ff.png?style=flat)
# Font Subset Extractor
Creates woff font files from truetype files, including only a subset of the glyphs.<br>
This is based on Google's [sfntly](https://github.com/googlei18n/sfntly).

## Download ##

The maven artifacts are on [Bintray](https://bintray.com/programingjd/maven/info.jdavid.font.subset/view)
and [jcenter](https://bintray.com/search?query=info.jdavid.font.subset).

[Download](https://bintray.com/artifact/download/programingjd/maven/info/jdavid/font/subset/extractor/1.0.0/extractor-1.0.0.jar) the latest jar.

__Maven__

Include [those settings](https://bintray.com/repo/downloadMavenRepoSettingsFile/downloadSettings?repoPath=%2Fbintray%2Fjcenter)
 to be able to resolve jcenter artifacts.
```
<dependency>
  <groupId>info.jdavid.font.subset</groupId>
  <artifactId>extractor</artifactId>
  <version>1.0.0</version>
</dependency>
```
__Gradle__

Add jcenter to the list of maven repositories.
```
repositories {
  jcenter()
}
```
```
dependencies {
  compile 'info.jdavid.font.subset:extractor1.0.0'
}
```

## Usage ##


__java__
```java
final Extractor extractor = new Extractor(ttfBytes);
final byte[] woffBytes = extractor.woff("subset_string");
assert woffBytes.length > 0;
```