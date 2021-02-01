# Jonathan's UPnP Port-Forwarding API

## About

### Motivation

Often times when Jonathan wanted to play with friends on a self hosted Minecraft server the need to port-forward for the server caused a lot of issues. The technical challenge therefore motivated Jonathan for an automated solution to the port-forwarding issue.

### Advantages

- Lightweight
- Easy to use
- Uses UPnP protocol, therefore, no need for authentication
- Single file, therefore, easy to incorperate into exisiting project

## Usage

**Note: Not all routers support UPnP**

### Open ports

```Java
//Open TCP port of 255
UPnP.openTCPPort(255);

//Open UDP port of 255
UPnP.openUDPPort(255);
```

### Close ports

```Java
//Close TCP port of 255
UPnP.closeTCPPort(255);

//Close UDP port of 255
UPnP.closeUDPPort(255);
```

## Testing Advice

After running one of the open or close methods check on sites like [https://www.canyouseeme.org/](https://www.canyouseeme.org/) to see if your port is opened or closed. **Make sure to have some applicaton listening on opened or closed port.** Or else all requests to specified port will result in no response.
