import heapq

class DiscreteEventSimulation:

    queue = []
    entryOrder = 0
    currentTime = 0
    previousEventTime = 0
        
    def scheduleAt(self, time, event):
        heapq.heappush(self.queue, (time, self.entryOrder, True, event))
        self.entryOrder += 1

    def cancel(self, predicate):
        queue = [(time, entryOrder, not predicate(event), event) for item in self.queue]

    def now(self):
        return self.currentTime

    def init(self):
        pass
    
    def handleMessage(self, event):
        pass
    
    def run(self):
        self.queue = []
        self.currentTime = 0
        self.init()
        self.previousEventTime = 0
        while len(self.queue) > 0:
            msg = heapq.heappop(self.queue)
            (time, entryOrder, active, event) = msg
            if active:
                self.currentTime = time
                self.handleMessage(event)
                self.previousEventTime = self.currentTime

class MyDES(DiscreteEventSimulation):
    def init(self):
        self.scheduleAt(3, "Clear drains")
        self.scheduleAt(4, "Feed cat")
        self.scheduleAt(5, "Make tea")
        self.scheduleAt(1, "Solve RC tasks")
        self.scheduleAt(2, "Tax return")

    def handleMessage(self, event):
        print(self.now(), event)

des = MyDES()
des.run()

class MyDES2(MyDES):
    def handleMessage(self, event):
        pass

des2 = MyDES2()
for i in range(100000):
    des2.run()
print("Done")

