#!/usr/bin/env groovy

class Globals {
    public static String g;
    public static def osMap = [
        "darwin" : "apple-darwin",
        "windows" : "pc-windows-msvc-shared",
        "linux" : "unknown-linux-gnu",
    ]
    public static def archMap = [
        "amd64" : "x86_64",
        "arm64" : "aarch64",
    ]

    public static def download = [
        "linux"   : [ "amd64", "arm64" ],
        "darwin"  : [ "amd64", "arm64" ],
        "windows" : [ "amd64" ],
    ]
}



//def token = getProp('github.token');
//def gh = new Github(token);
//def releases = [:];
//
//def releaseCount = 10;
//
//def repo = "indygreg/python-build-standalone";
//
//gh.listReleases(repo, {release ->
//    gh.listAssets(repo, release.id, {asset->
//        def parts = parseName(asset.name)
//        if (!parts) { return true; }
//        if (!Globals.osMap.containsValue(parts.os)) { return true; }
//        if (!Globals.archMap.containsValue(parts.arch)) { return true; }
//        //println parts
//
//        //
//        if (!releases[parts.version]) {
//            releases[parts.version] = [oses:[:], build: (int) release.id ];
//        }
//
//        if (!releases[parts.version].oses.containsKey(parts.os)) {
//            releases[parts.version].oses[parts.os] = [];
//        }
//
//        if (!releases[parts.version].oses[parts.os].contains(parts.arch)) {
//            releases[parts.version].oses[parts.os] += parts.arch;
//        }
//        if (releases[parts.version].build < release.id) {
//            releases[parts.version].build = release.id;
//        }
//
//        sleep(200);
//    })
//    
//});
//println groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(releases))
//
//def parseName(name) {
//    def parts;
//    def matcher = name =~ /cpython-(\d[^+]+)\+(\d+)-(.*?)-(.*?)-install_only.tar.gz/
//    if (matcher.matches()) {
//        parts = [ version:matcher[0][1], build:matcher[0][2], arch:matcher[0][3], os:matcher[0][4] ]
//    }
//    return parts
//}

def build = "20230116"
def version = "3.11.1"
Globals.download.each { os, arches ->
    arches.each { arch ->
        download(os, arch, version, build);
    }
}


/*
Globals.osMap.entrySet().each { e1 ->
    def os = e1.getKey()
    Globals.archMap.entrySet() each { e2 ->
        def arch = e2.getKey()
        download(os, arch, version, build);
    }
}
*/



def download(argOs, argArch, version , build) {
    def os   = Globals.osMap[argOs]
    def arch = Globals.archMap[argArch]
    def base = "https://github.com/indygreg/python-build-standalone/releases/download/$build/"
    def name = "cpython-$version+$build-$arch-$os-install_only.tar.gz"
    def url = "$base/$name"
    println "Downloading $name ..."
    //System.exit 1
    def proc = ["wget", "$url", "-O", "$name"].execute()
    proc.consumeProcessOutput(System.out, System.err)
    def ret = proc.waitFor()
    if (ret!=0) {
        throw new RuntimeException("Error downloading $url")
    }
    println "$name ... Done"
}


/*
cpython-3.10.9+20230116-aarch64-apple-darwin-install_only.tar.gz
cpython-3.10.9+20230116-aarch64-unknown-linux-gnu-install_only.tar.gz
cpython-3.10.9+20230116-aarch64-unknown-linux-gnu-install_only.tar.gz
*/

def getProp(key) {
    def val = null;

    //
    def propKey = toPropKey(key)
    if (System.properties.containsKey(propKey)) {
        return System.properties.get(propKey);
    }

    //
    def envKey = toEnvKey(key)
    def env = System.getenv()
    if (env.containsKey(envKey)) {
        return env.get(envKey);
    }

	rhaProps = loadProps(new File(System.properties.get("user.home") + "/.rha/config.properties"))
    if (rhaProps.containsKey(propKey)) {
        return rhaProps.get(propKey)
    }

    throw new RuntimeException("Environment variable `$envKey` or Property `$propKey` not found");
}

def toEnvKey(str) { return str.replace('.', '_') .replace('-', '_') .toUpperCase(); }
def toPropKey(str) { return str.replace('-', '.') .replace('_', '.') .toLowerCase(); }
def loadProps(File file) { def props = new Properties();
    if (file.exists()) { file.withInputStream { props.load(it ); } }
    return props;
}

class Request {

    private String url;
    private Map<String, String> headers = [:];

    public Request(String url) {
        this.url = url;
    }

    public header(String key, String value) {
        this.headers.put(key, value);
        return this;
    }

    public Response get() {
        def url = new java.net.URL(this.url)
        def connection = url.openConnection();
        connection.requestMethod = "GET";
        this.headers.each { k,v -> connection.setRequestProperty(k, v); }
        def responseCode = connection.responseCode
        def inputStream = responseCode < java.net.HttpURLConnection.HTTP_BAD_REQUEST ? connection.inputStream : connection.errorStream
        //def reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream))
        //def response = reader.readLine()
        // Close the connection and resources
        //reader.close()
        //connection.disconnect()
        return new Response(connection, responseCode, inputStream);
    }
}

class Response {
    private int status;
    private InputStream input;
    private java.net.HttpURLConnection connection;
    private Map<String, String> headers;
    public Response(java.net.HttpURLConnection connection, int status, InputStream input) {
        this.connection = connection;
        this.status = status;
        this.input = input;
        this.headers = connection.getHeaderFields();
    }

    public getHeaders() {
        return headers;
    }

    def getHeader(String key) {
        return headers.get(key);
    }

    def getString() {
        //def reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream));
        //def response = reader.readLine()
        //reader.close()
        return input.getText("UTF-8") ;
    }

    def getJson() {
        return new groovy.json.JsonSlurper().parseText(getString());
    }


    def close() {
        connection.disconnect();
    }
}



class Github {
    def token;
    public Github(token) {
        this.token = token;
    }

    def listReleases(repo, closure) {
        def url = "https://api.github.com/repos/$repo/releases";
        list(url, closure);
    }

    def listAssets(repo, releaseId, closure) {
        def url = "https://api.github.com/repos/$repo/releases/${releaseId}/assets"
        list(url, closure);
    }

    def list(url, closure) {
        def next = url;
        while (next) {
            try (
                def resp = new Request(next)
                    .header("Authorization", "token $token")
                    .get()
            ) {
                def data = resp.getJson();
                for (obj in data) {
                    def ret = closure(obj);
                    if (ret instanceof Boolean) {
                        if (!ret) {
                            return;
                        }
                    }
                }
                next = getNext(resp)
            }
        }
    }

    def getNext(response) {
        def next = null
        for (link in response.getHeader('Link')?[0]?.split(',')) {
          def matcher = link =~ /<(.*\?page=\d+)>; rel="next"/
          if (matcher.matches()) {
            //next = [url:matcher[0][1]]
            next = matcher[0][1];
          }
        }
        return next
    }
}
