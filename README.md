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

### Плата сбора температур с датчиков DS18B20 N1
**Модель** - R4DCB08, документация TODO

**Настройки**
* выставлен адрес 1 командой **TODO**
* адрес указан в параметре "temperatureSensorsBoard.address"

**Подключения**:
* к входам GND/D/5V подключены температурные датчики DS18B20, регистры начинаются с 0 и прописываются в enum TemperatureSensor

### Реле CHINALCTECH (bestep) 1IN-1OUT N1
Реле управления газовым котлом.
На вход принимает состояние реле байпаса через optocoupler (гальваническую развязку).

**Модель** - неизвестна, документация TODO

**Настройки**:
* выставлен адрес 2 командой **TODO**
* адрес указан в параметрах "bypass.relay.address", "gasBoiler.relay.address"

**Подключения**:
* к GND_IN и IN1 подключено реле байпаса (через цепь питания), параметр "bypass.relay.discreteInput=0"
* к COM1 и NС1 подключен выход комнатного термостат газового котла (нормально открытый), параметр "gasBoiler.relay.coil=0"

**NOTE**
Газовый котел и насос теплых полов подключены к нормально закрытым выходам реле, то есть при отключении реле их цепи замкнуты.
Это сделано для того, чтобы при отказе системы управления повысить вероятного того, что котел и насос останутся включенными.
Управление реле инвертировано.

### Реле CHINALCTECH (bestep) 2IN-2OUT N1
Реле управления насосом теплого пола и реле питания сервопривода теплого пола.

**Модель** - неизвестна, документация в docs/CHINALTECH_2IN-2OUT_RELAY

**Настройки**:
* выставлен адрес 3 командой **TODO**
* адрес указан в параметрах "floorHeating.pump.relay.address", "floorHeating.valve.relay"

**Подключения**:
* к COM2 и NO1 подключен насос теплых полов, параметр "floorHeating.pump.relay.coil=0"
* к COM1 и NO2 подключено питание сервопривода теплых полов, параметр "floorHeating.valve.relay.coil=1"

### Цифро-аналоговой преобразователь N4DAC02
Цифро-аналоговый преобразователь (DAC), управляющий положением клапана подмеса теплого пола.

**Модель** - N4DAC02, документация TODO

**Настройки**:
* выставлен адрес 4 командой **TODO**
* адрес указан в параметрах "floorHeating.valve.dac.address"

TODO - куда подключен?

### Реле CHINALCTECH (bestep) 1IN-1OUT N2
Реле управления электрическим котлом

**Модель** - неизвестна, документация TODO

**Настройки**:
* выставлен адрес 5 командой **TODO**
* адрес указан в параметрах **TODO**

**Подключения**:
* к COM1 и NO1 подключен выход комнатного термостат электрического котла (нормально открытый), параметр **TODO**

### Реле CHINALCTECH (bestep) 2IN-2OUT N2
Реле управления освещением на улице и обогревом воронок.

**Модель** - неизвестна, документация в docs/CHINALTECH_2IN-2OUT_RELAY

**Настройки**:
* выставлен адрес 6 командой **TODO**
* адрес указан в параметрах "streetLight.relay.address", "funnelHeating.relay.address"

**Подключения**:
* к COM1 и NO1 подключена цепь уличного освещения, параметр "streetLight.relay.coil=0"
* к COM1 и NO2 подключена цепь обогрева воронок на крыше, параметр "funnelHeating.relay.coil=1"

### Сервопривод управления подмесом в теплые полы ESBE
**Модель** - ARA-659, документация в docs/ARA-659
Управляется через сигнал 0-10В с ЦАП N4DAC02, питается через реле CHINALCTECH (bestep) 2IN-2OUT N1
Время поворота выставлено на 45 секунд, на это время подается питание (параметр "floorHeating.valve.relay.delay")