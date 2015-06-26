
import java.nio.file.Files;
import java.util.regex.Pattern
def fin = new File('raw.html')
def fout = new File('out.html')

if(fout.exists()){
	Files.copy(fout.toPath(), new File(fout.absolutePath+"_"+System.nanoTime()).toPath())
	fout.delete()
}

def bout = new BufferedWriter(new FileWriter(fout));

int skipNext = 0
boolean removeNextA = false
def lineQueue = []
int linesIn = 0
fin.eachLine {
	linesIn++
	String line = it;

	if(skipNext<=0){
		def splits = line.split("<")
		boolean first = true;

		splits.each{
			if(!first){
				lineQueue << "<"+it
			}else{
				lineQueue << it
				first = false
			}
		}
	}else{
		skipNext--;
	}
}

def SCRIPT = 'script'
def skipUntil = null

skipNext = 0
removeNextA = false
int linesOut = 0

def hitBody = false
boolean firstMessage = true

def namesKnown = [] as Set
def finalQueue = []

def skipToNextName = false
lineQueue.each {
	boolean writeln = true;

	String line = it;
	if(skipUntil){
	}else  if(!hitBody){
		if(line.startsWith("<body")){
			hitBody = true
			writeln = false
		}else{
			writeln = false
		}
	}else if(skipNext>0){
		skipNext--
		writeln = false
	}else if(line.startsWith('<')&&(!line.startsWith('<p'))&&!line.startsWith('<a ')&&!line.startsWith('<abbr')&&!line.startsWith('<img')){
		writeln = false
	}

	boolean newMessage = false

	if(writeln){
		if(line.startsWith('<a ')){
			if(line.contains('hidden_elem')||line.contains('"mrs _')||line.contains('uiCloseButton')||line.contains('uiBoxLightblue')){
				writeln = false
			}else if(line.contains('data-hovercard')){
				def name = line.substring(line.indexOf(">")+1)
				def nameClass = "person${name.hashCode()}"
				if(!name){
					skipToNextName = true;
				}else{
					if(!namesKnown.contains(name)){
						namesKnown<<name
						//TODO maybe add a style here
					}

					line = "<div class='message $nameClass'><div class='name'>"+name+"</div>"
					if(firstMessage){
						firstMessage=false;
					}else{
						line = "</div>"+line
					}
					newMessage = true;
				}
			}else if(firstMessage){
				writeln = false
			}else {
				line = line+"</a><br>"
			}
		}else if(line.startsWith('<abbr')){
			if(firstMessage){
				writeln = false
			}else{
				line = line+"</abbr>"
			}
		}else if(line.startsWith('<img')){
			if(firstMessage){
				writeln = false
			}else if(!line.contains('_50dv')&&!line.contains('_9g')&&!line.contains('width="1"')&&!line.contains('hidden_elem')){
				line = line+"</img>"
			}else{
				writeln = false
			}
		}
	}


	if(skipToNextName){
		if(newMessage){
			skipToNextName = false
		}else{
			writeln = false
		}
	}

	if(writeln){
		finalQueue << line
		linesOut++
	}
}

lineQueue = null

def colors = ['lightblue', 'antiquewhite']
def index = 0;
bout << """ <html>
<head>
<style>
abbr {
  color: grey;
}
.message {
  margin-bottom: 10px;
}
p {
    margin-bottom: 1px;
    -webkit-margin-after: 1px;
    -webkit-margin-before: 1px;
    padding-bottom: 1px;
}
.name {
  font-weight: bold;
  background-color: rgba(0,0,0,0.1);
}
"""
namesKnown.each { 
	bout << ".person${it.hashCode()}{  background-color:${colors[index]} }\r\n"
	index++
	index%=colors.size()
}

bout << """</style>
</head>
<body>
<div class='header'>ConvoParser</div>
"""

finalQueue.each{ bout << it + "\r\n" }

bout<<"""
</div>
<div class='footer'>End Of Data</div>
</body>
</html>"""

bout.flush()
bout.close()

println "Lines In $linesIn, lines out $linesOut"
