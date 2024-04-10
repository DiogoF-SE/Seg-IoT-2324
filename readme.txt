PARA COMPILAR:

javac IoTServer.java

javac IoTDevice.java

PARA EXECUTAR:

java IoTServer :port   (se port nao for inserido o porto default é 12345)

java IoTDevice localhost porto deviceid userid

Para enviar imagens o client deve guardar a imagem na pasta gerada com o nome clientImages, essa 
imagem é guardada no servidor na pasta images.

Ao receber imagens, a imagem é guardada no diretorio gerado no cliente com o nome receivedImages, e é sempre com 
o nome image + extensao usada, de forma a apagar/sobrepor a antiga imagem.

Os dados recebidos de temperatura, sao recebidos no temperature_data.txt criado no client, e nao apaga os registos
anteriors, estes sao append.

1# Limitação, se um user já registado e errar a password, o IoTDevice faz exit em vez de voltar a pedir a password

2# Limitação, quando nenhum device enviou dados de temperatura, ao fazer RT o cliente fica preso em loop

Penso que tudo o resto funciona normalmente