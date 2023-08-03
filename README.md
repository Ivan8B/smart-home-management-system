## Список устройств и их настройки

### Шлюз modbus-tcp
**Модель** - USR-DR302, документация в docs/USR-DR302.

**Настройки**:
* включен DHCP клиент
* в разделе "Serial Port": Baud Rate"="9600", "Data Size" = "8 bit", "Parity Mode" = "None", "Stop Bits" = "1", "Local Port Number" = "8234", "Work Mode" = "TCP Server"
* включен "Modbus Gateway" (в разделе Expand Function)
* отключена опция "TCP Server-kick off old connection" (в разделе Expand Function)

Нужно прописать в /etc/hosts связку hostname-ip ds302 и заполнить в application.yml параметры modbus.tcpHost(из /etc/hosts) и modbus.tcpPort(параметр "Local Port Number" на DR302).

**Подключения**:
* по MODBUS шине подключены все устройства ниже

### Реле CHINALCTECH (bestep) 2IN-2OUT N1
Реле управления газовым и электрическим котлом.
На вход принимает состояние реле байпаса через optocoupler (гальваническую развязку).

**Модель** - неизвестна, документация в docs/CHINALTECH_2IN-2OUT_RELAY

**Настройки**:
* выставлен адрес 1 командой **00 10 00 00 00 01 02 00 01 6A 00**
* адрес указан в параметрах "bypass.relay.address", "gasBoiler.relay.address", "//TODO'

**Подключения**:
* к GND_IN и IN1 подключено реле байпаса (через цепь питания), параметр "bypass.relay.discreteInput=0"
* к COM1 и NO1 подключен выход комнатного термостат газового котла (нормально открытый), параметр "gasBoiler.relay.coil=0"
* к COM2 и NO2 подключен насос теплых полов, параметр "//TODO"

### Плата сбора температур с датчиков DS18B20 N1
**Модель** - R4DCB08, документация в docs/R4DCB08

**Настройки**
* выставлен адрес 2 командой **01 06 00 FE 00 02 69 FB**
* адрес указан в параметре "temperatureSensorsBoard.address"

**Подключения**:
* к входам GND/D/5V подключены температурные датчики DS18B20, регистры начинаются с 0 и прописываются в enum TemperatureSensor

### Реле CHINALCTECH (bestep) 2IN-2OUT N2
Реле управления освещением на улице и обогревом воронок.

**Модель** - неизвестна, документация в docs/CHINALTECH_2IN-2OUT_RELAY

**Настройки**:
* выставлен адрес 3 командой **TODO**
* адрес указан в параметрах "streetLight.relay.address", "funnelHeating.relay.address"

**Подключения**:
* к COM1 и NO1 подключена цепь уличного освещения, параметр "streetLight.relay.coil=0"
* к COM1 и NO2 подключена цепь обогрева воронок на крыше, параметр "funnelHeating.relay.coil=1"